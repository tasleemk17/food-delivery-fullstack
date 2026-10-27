import json
import uuid

import pytest
try:
    from testcontainers.community.postgres import PostgresContainer
except ImportError:  # older testcontainers
    from testcontainers.postgres import PostgresContainer

from app.catalog import Food
from app.config import Settings
from app.db import create_pool, migrate


@pytest.fixture(scope="session")
def database_url():
    # A real PostgreSQL, like in the other services' tests: the SQL is what must be proven
    with PostgresContainer("postgres:16-alpine", driver=None) as pg:
        yield pg.get_connection_url()


@pytest.fixture
def pool(database_url):
    pool = create_pool(database_url)
    migrate(pool)
    with pool.connection() as conn:
        conn.execute("TRUNCATE processed_events, food_stats, food_pairs")
    yield pool
    pool.close()


@pytest.fixture
def settings():
    return Settings(kafka_retry_initial_seconds=0.01, kafka_retry_max_seconds=0.02, kafka_max_retries=3)


class FakeCatalog:
    def __init__(self, foods=None, availability=None):
        self._foods = foods if foods is not None else MENU
        self._availability = availability or {}

    def foods(self):
        return self._foods

    def availability(self):
        return self._availability


def food(id, name, category, price, description=""):
    return Food(id=id, name=name, description=description, price=price, category=category, image=f"{id}.png")


MENU = [
    food("f1", "Greek Salad", "Salad", 12, "Fresh tomatoes, cucumber, feta and olives"),
    food("f2", "Veg Salad", "Salad", 18, "Lettuce, carrot and sprouts"),
    food("p1", "Creamy Pasta", "Pasta", 14, "Pasta in white sauce with mushrooms"),
    food("p2", "Cheese Pasta", "Pasta", 12, "Baked pasta with three cheeses"),
    food("n1", "Veg Noodles", "Noodles", 14, "Stir fried noodles with vegetables"),
]


def order_event(foods, type="ORDER_PAID", payment="Stripe", event_id=None):
    return json.dumps({
        "eventId": str(event_id or uuid.uuid4()),
        "type": type,
        "order": {"orderId": "o1", "paymentMethod": payment,
                  "items": [{"foodId": i, "name": n, "quantity": 1, "unitPrice": 10} for i, n in foods]},
    }).encode()
