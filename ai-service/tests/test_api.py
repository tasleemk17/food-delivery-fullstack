import pytest
from fastapi.testclient import TestClient

from app.chat import ChatService
from app.consumer import EventHandler, KafkaWorker
from app.events import parse_order_event
from app.knowledge import KnowledgeBase
from app.main import create_app
from app.ratelimit import SlidingWindowLimiter
from app.recommender import RecommendationStore
from tests.conftest import FakeCatalog, order_event
from tests.test_chat_and_llm import FakeLLM


def make_client(pool, settings, availability=None, llm=None, limit=20, worker=None):
    store = RecommendationStore(pool)
    catalog = FakeCatalog(availability=availability)
    chat = ChatService(catalog, KnowledgeBase(), llm, settings)
    app = create_app(settings, store, catalog, chat, SlidingWindowLimiter(limit, 600), worker)
    return TestClient(app), store


def order(store, *ids):
    store.record(parse_order_event(order_event([(i, i) for i in ids])))


def ids(response):
    return [f["_id"] for f in response.json()["data"]]


# ---- recommendations --------------------------------------------------------------------------

def test_recommends_what_was_ordered_together(pool, settings):
    client, store = make_client(pool, settings)
    order(store, "f1", "p1")
    order(store, "f1", "p1")
    order(store, "f1", "n1")

    body = client.get("/api/ai/recommendations", params={"foodIds": "f1", "limit": 2}).json()
    assert [f["_id"] for f in body["data"]] == ["p1", "n1"] and body["basis"] == "together"
    assert body["data"][0]["name"] == "Creamy Pasta" and body["data"][0]["price"] == 14


def test_sold_out_and_unknown_dishes_are_not_recommended(pool, settings):
    client, store = make_client(pool, settings, availability={"p1": False})
    order(store, "f1", "p1")
    order(store, "f1", "gone-from-menu")
    order(store, "f1", "n1")
    assert ids(client.get("/api/ai/recommendations", params={"foodIds": "f1"})) == ["n1"]


def test_popular_dishes_fill_up_the_list(pool, settings):
    client, store = make_client(pool, settings)
    order(store, "f1", "p1")
    order(store, "n1")
    order(store, "n1")

    body = client.get("/api/ai/recommendations", params={"foodIds": "f1", "limit": 3}).json()
    assert [f["_id"] for f in body["data"]] == ["p1", "n1"]  # nothing else has been ordered
    assert "f1" not in ids(client.get("/api/ai/recommendations", params={"foodIds": "f1", "limit": 5}))


def test_without_a_cart_the_popular_dishes_are_shown(pool, settings):
    client, store = make_client(pool, settings)
    order(store, "n1")
    order(store, "n1")
    order(store, "p1")
    response = client.get("/api/ai/recommendations")
    assert ids(response) == ["n1", "p1"] and response.json()["basis"] == "popular"
    assert ids(client.get("/api/ai/recommendations/popular", params={"limit": 1})) == ["n1"]


def test_no_history_gives_an_empty_list(pool, settings):
    client, _ = make_client(pool, settings)
    body = client.get("/api/ai/recommendations", params={"foodIds": "f1"}).json()
    assert body == {"success": True, "data": [], "basis": "none"}


@pytest.mark.parametrize("params", [{"limit": 0}, {"limit": 99}, {"limit": "x"}])
def test_bad_limits_are_rejected(pool, settings, params):
    client, _ = make_client(pool, settings)
    assert client.get("/api/ai/recommendations", params=params).status_code == 422


# ---- chat -------------------------------------------------------------------------------------

def test_chat_needs_a_logged_in_user(pool, settings):
    client, _ = make_client(pool, settings)
    assert client.post("/api/ai/chat", json={"message": "hi"}).status_code == 401


def test_chat_answers(pool, settings):
    client, _ = make_client(pool, settings, llm=FakeLLM("Try the pasta."))
    response = client.post("/api/ai/chat", json={"message": "what pasta?"}, headers={"X-User-Id": "u1"})
    data = response.json()["data"]
    assert response.status_code == 200
    assert data["answer"] == "Try the pasta." and data["mode"] == "ai" and data["degraded"] is False
    assert {"kind": "menu", "title": "Creamy Pasta"} in data["sources"]


def test_chat_works_without_claude(pool, settings):
    client, _ = make_client(pool, settings)
    data = client.post("/api/ai/chat", json={"message": "noodles"}, headers={"X-User-Id": "u1"}).json()["data"]
    assert data["mode"] == "search" and "Veg Noodles" in data["answer"]


@pytest.mark.parametrize("message", ["", "   ", "x" * 501])
def test_bad_messages_are_rejected(pool, settings, message):
    client, _ = make_client(pool, settings)
    response = client.post("/api/ai/chat", json={"message": message}, headers={"X-User-Id": "u1"})
    assert response.status_code == 422 and response.json()["success"] is False


def test_chat_is_rate_limited_per_user(pool, settings):
    client, _ = make_client(pool, settings, limit=2)
    send = lambda user: client.post("/api/ai/chat", json={"message": "pasta"}, headers={"X-User-Id": user})
    assert send("u1").status_code == 200 and send("u1").status_code == 200
    blocked = send("u1")
    assert blocked.status_code == 429 and int(blocked.headers["Retry-After"]) >= 1
    assert send("u2").status_code == 200  # someone else is not affected


def test_health_reports_the_chat_mode(pool, settings):
    client, _ = make_client(pool, settings)
    assert client.get("/health").json() == {"status": "UP", "chatMode": "search"}


# ---- the Kafka worker -------------------------------------------------------------------------

class FakeProducer:
    def __init__(self):
        self.sent = []

    def produce(self, topic, key=None, value=None, headers=None):
        self.sent.append((topic, key, value, headers))

    def flush(self, timeout):
        return 0


def worker_for(pool, settings, handler=None):
    producer = FakeProducer()
    handler = handler or EventHandler(RecommendationStore(pool))
    return KafkaWorker(settings, handler, consumer=object(), producer=producer, sleep=lambda s: None), producer


def test_a_valid_message_is_counted(pool, settings):
    worker, producer = worker_for(pool, settings)
    worker.process(b"o1", order_event([("a", "A"), ("b", "B")]))
    assert RecommendationStore(pool).events_counted() == 1 and producer.sent == []


def test_a_broken_message_goes_straight_to_the_dead_letter_topic(pool, settings):
    worker, producer = worker_for(pool, settings)
    worker.process(b"o1", b"not json")
    assert producer.sent[0][0] == "order-events.ai.DLT" and producer.sent[0][2] == b"not json"


def test_a_failing_message_is_retried_then_dead_lettered(pool, settings):
    class Failing:
        calls = 0

        def handle(self, raw):
            Failing.calls += 1
            raise RuntimeError("database down")

    worker, producer = worker_for(pool, settings, Failing())
    worker.process(b"o1", order_event([("a", "A")]))
    assert Failing.calls == settings.kafka_max_retries and len(producer.sent) == 1


def test_a_temporary_failure_recovers_without_a_dead_letter(pool, settings):
    real = EventHandler(RecommendationStore(pool))

    class Flaky:
        calls = 0

        def handle(self, raw):
            Flaky.calls += 1
            if Flaky.calls < 3:
                raise RuntimeError("blip")
            return real.handle(raw)

    worker, producer = worker_for(pool, settings, Flaky())
    worker.process(b"o1", order_event([("a", "A")]))
    assert RecommendationStore(pool).events_counted() == 1 and producer.sent == []
