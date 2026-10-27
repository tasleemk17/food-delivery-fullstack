"""The order events that order-service writes to Kafka, reduced to what this service needs."""
from __future__ import annotations

import json
from dataclasses import dataclass
from uuid import UUID


class InvalidEvent(ValueError):
    """The message is not a usable order event. Retrying cannot fix that."""


@dataclass(frozen=True)
class OrderedFood:
    food_id: str
    name: str


@dataclass(frozen=True)
class OrderEvent:
    event_id: UUID
    type: str
    payment_method: str
    foods: tuple[OrderedFood, ...]

    @property
    def is_confirmed_order(self) -> bool:
        """
        True for the moment an order becomes real: Stripe confirmed the payment, or a cash on delivery
        order was placed. An unpaid Stripe order, a failed payment or a status change does not count.
        """
        return self.type == "ORDER_PAID" or (self.type == "ORDER_PLACED" and self.payment_method == "COD")


def parse_order_event(raw: str | bytes) -> OrderEvent:
    """Unknown fields are ignored, so order-service can add fields without breaking this service."""
    try:
        data = json.loads(raw)
        event_id = UUID(str(data["eventId"]))
        event_type = str(data["type"])
        order = data["order"]
        payment_method = str(order.get("paymentMethod") or "")
        items = order.get("items") or []
        foods = tuple(
            OrderedFood(food_id=str(item["foodId"]), name=str(item.get("name") or item["foodId"]))
            for item in items
        )
    except (ValueError, KeyError, TypeError, AttributeError) as e:
        raise InvalidEvent(f"Not a valid order event: {e}") from e
    return OrderEvent(event_id=event_id, type=event_type, payment_method=payment_method, foods=foods)
