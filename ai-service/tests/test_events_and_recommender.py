import threading
import uuid

import pytest

from app.events import InvalidEvent, parse_order_event
from app.recommender import RecommendationStore
from tests.conftest import order_event


def parsed(*args, **kwargs):
    return parse_order_event(order_event(*args, **kwargs))


# ---- parsing ----------------------------------------------------------------------------------

def test_a_paid_stripe_order_is_confirmed():
    assert parsed([("a", "A")], type="ORDER_PAID").is_confirmed_order


def test_a_cash_order_is_confirmed_when_placed():
    assert parsed([("a", "A")], type="ORDER_PLACED", payment="COD").is_confirmed_order


@pytest.mark.parametrize("type,payment", [("ORDER_PLACED", "Stripe"), ("ORDER_PAYMENT_FAILED", "Stripe"),
                                          ("ORDER_STATUS_CHANGED", "COD")])
def test_other_events_are_not_confirmed_orders(type, payment):
    assert not parsed([("a", "A")], type=type, payment=payment).is_confirmed_order


@pytest.mark.parametrize("raw", [b"not json", b"{}", b'{"eventId": "x", "type": "T", "order": {}}',
                                 b'{"eventId": "%s", "type": "T"}' % str(uuid.uuid4()).encode(), b"[]", b"null"])
def test_garbage_is_an_invalid_event(raw):
    with pytest.raises(InvalidEvent):
        parse_order_event(raw)


def test_unknown_fields_are_ignored():
    raw = order_event([("a", "A")]).replace(b'"orderId"', b'"somethingNew": 1, "orderId"')
    assert parse_order_event(raw).foods[0].food_id == "a"


# ---- counting ---------------------------------------------------------------------------------

def test_foods_ordered_together_are_counted_in_both_directions(pool):
    store = RecommendationStore(pool)
    assert store.record(parsed([("pasta", "Pasta"), ("salad", "Salad")]))
    assert store.record(parsed([("pasta", "Pasta"), ("salad", "Salad"), ("noodles", "Noodles")]))

    assert [(s.food_id, s.score) for s in store.related(["pasta"], 5)] == [("salad", 2), ("noodles", 1)]
    assert [(s.food_id, s.score) for s in store.related(["salad"], 5)] == [("pasta", 2), ("noodles", 1)]


def test_the_same_event_is_counted_once(pool):
    store = RecommendationStore(pool)
    event = parsed([("a", "A"), ("b", "B")])
    assert store.record(event)
    assert not store.record(event)  # Kafka delivered it twice
    assert store.popular(5)[0].score == 1
    assert store.related(["a"], 5)[0].score == 1
    assert store.events_counted() == 1


def test_concurrent_deliveries_of_one_event_count_once(pool):
    store = RecommendationStore(pool)
    event = parsed([("a", "A"), ("b", "B")])
    results = []
    threads = [threading.Thread(target=lambda: results.append(store.record(event))) for _ in range(5)]
    [t.start() for t in threads]
    [t.join() for t in threads]
    assert results.count(True) == 1
    assert store.popular(1)[0].score == 1


def test_unconfirmed_orders_and_empty_orders_are_not_counted(pool):
    store = RecommendationStore(pool)
    assert not store.record(parsed([("a", "A")], type="ORDER_PLACED", payment="Stripe"))
    assert not store.record(parsed([], type="ORDER_PAID"))
    assert store.events_counted() == 0


def test_quantity_does_not_matter_and_repeats_in_one_order_count_once(pool):
    store = RecommendationStore(pool)
    assert store.record(parsed([("a", "A"), ("a", "A"), ("b", "B")]))
    assert store.popular(5)[0].score == 1
    assert store.related(["a"], 5)[0].score == 1


def test_related_adds_up_for_a_whole_cart_and_leaves_the_cart_out(pool):
    store = RecommendationStore(pool)
    store.record(parsed([("a", "A"), ("x", "X")]))
    store.record(parsed([("b", "B"), ("x", "X")]))
    store.record(parsed([("a", "A"), ("y", "Y")]))

    result = {s.food_id: s.score for s in store.related(["a", "b"], 5)}
    assert result == {"x": 2, "y": 1}


def test_popular_is_ordered_by_orders(pool):
    store = RecommendationStore(pool)
    store.record(parsed([("a", "A")]))
    store.record(parsed([("b", "B")]))
    store.record(parsed([("b", "B")]))
    assert [s.food_id for s in store.popular(5)] == ["b", "a"]


def test_empty_and_zero_limit_cases(pool):
    store = RecommendationStore(pool)
    assert store.related([], 5) == []
    assert store.related(["a"], 0) == []
    assert store.related(["never-ordered"], 5) == []
    assert store.popular(0) == []
