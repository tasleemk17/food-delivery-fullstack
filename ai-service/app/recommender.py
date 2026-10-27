"""
"Customers who ordered this also ordered ..." built from the order events.

Two numbers per food are kept: in how many confirmed orders it appeared, and, for every
pair of foods, in how many confirmed orders both appeared. Quantities do not matter
("2 x pasta" counts once): the question is which dishes people put in the same order.
"""
from __future__ import annotations

import logging
from dataclasses import dataclass

from psycopg_pool import ConnectionPool

from .events import OrderEvent

log = logging.getLogger(__name__)


@dataclass(frozen=True)
class Scored:
    food_id: str
    score: int


class RecommendationStore:
    def __init__(self, pool: ConnectionPool) -> None:
        self._pool = pool

    def record(self, event: OrderEvent) -> bool:
        """
        Counts one confirmed order. Returns True if it was counted, False if it was skipped
        (not a confirmed order, no foods, or this event id was already counted).

        The event id is saved FIRST, in the same transaction as the counts. A counter is not safe
        to repeat (adding 1 twice gives a wrong number), and Kafka may deliver a message twice,
        so a repeated event must be recognised and ignored.
        """
        if not event.is_confirmed_order or not event.foods:
            return False

        names: dict[str, str] = {}
        for food in event.foods:
            names.setdefault(food.food_id, food.name)
        ids = sorted(names)  # always the same order, so two workers never lock rows in opposite orders

        with self._pool.connection() as conn:  # one transaction: all or nothing
            inserted = conn.execute(
                "INSERT INTO processed_events (event_id) VALUES (%s) ON CONFLICT DO NOTHING",
                (event.event_id,),
            ).rowcount
            if inserted == 0:
                log.info("Event %s was already counted", event.event_id)
                return False

            for food_id in ids:
                conn.execute(
                    "INSERT INTO food_stats (food_id, name, orders) VALUES (%s, %s, 1) "
                    "ON CONFLICT (food_id) DO UPDATE SET orders = food_stats.orders + 1, name = EXCLUDED.name",
                    (food_id, names[food_id]),
                )
            for a in ids:
                for b in ids:
                    if a != b:
                        conn.execute(
                            "INSERT INTO food_pairs (food_id, other_food_id, together) VALUES (%s, %s, 1) "
                            "ON CONFLICT (food_id, other_food_id) DO UPDATE SET together = food_pairs.together + 1",
                            (a, b),
                        )
        return True

    def related(self, food_ids: list[str], limit: int) -> list[Scored]:
        """
        The foods most often ordered together with ANY of food_ids (scores add up when several
        foods are given, for example the whole cart), best first. The given foods are left out.
        """
        if not food_ids or limit <= 0:
            return []
        with self._pool.connection() as conn:
            rows = conn.execute(
                "SELECT other_food_id, SUM(together)::int AS score FROM food_pairs "
                "WHERE food_id = ANY(%s) AND NOT (other_food_id = ANY(%s)) "
                "GROUP BY other_food_id ORDER BY score DESC, other_food_id LIMIT %s",
                (food_ids, food_ids, limit),
            ).fetchall()
        return [Scored(food_id=r[0], score=r[1]) for r in rows]

    def popular(self, limit: int) -> list[Scored]:
        if limit <= 0:
            return []
        with self._pool.connection() as conn:
            rows = conn.execute(
                "SELECT food_id, orders FROM food_stats ORDER BY orders DESC, food_id LIMIT %s",
                (limit,),
            ).fetchall()
        return [Scored(food_id=r[0], score=r[1]) for r in rows]

    def events_counted(self) -> int:
        with self._pool.connection() as conn:
            return conn.execute("SELECT count(*) FROM processed_events").fetchone()[0]
