"""
The current menu and which dishes are in stock, read from the two services that own them.

Both are PUBLIC endpoints (the website itself uses them), so no key is needed. Answers are kept
for a short time, so a chat message does not make two web calls every time. If a service is down
the last answer is used; if there never was one, the result is empty and the callers cope with that.
"""
from __future__ import annotations

import logging
import threading
import time
from dataclasses import dataclass
from typing import Callable, Protocol

import httpx

log = logging.getLogger(__name__)


@dataclass(frozen=True)
class Food:
    id: str
    name: str
    description: str
    price: float
    category: str
    image: str


class Catalog(Protocol):
    def foods(self) -> list[Food]: ...

    def availability(self) -> dict[str, bool]:
        """Only foods whose stock is tracked appear here: True = some left, False = out of stock."""
        ...


class HttpCatalog:
    def __init__(self, catalog_url: str, inventory_url: str, cache_seconds: float = 30.0,
                 timeout_seconds: float = 3.0, clock: Callable[[], float] = time.monotonic) -> None:
        self._catalog_url = catalog_url
        self._inventory_url = inventory_url
        self._ttl = cache_seconds
        self._client = httpx.Client(timeout=timeout_seconds)
        self._clock = clock
        self._lock = threading.Lock()
        self._cache: dict[str, tuple[float, object]] = {}

    def foods(self) -> list[Food]:
        data = self._cached("foods", lambda: self._get_json(f"{self._catalog_url}/api/food/list"), [])
        foods: list[Food] = []
        for item in data if isinstance(data, list) else []:
            try:
                foods.append(Food(
                    id=str(item["_id"]),
                    name=str(item.get("name") or ""),
                    description=str(item.get("description") or ""),
                    price=float(item.get("price") or 0),
                    category=str(item.get("category") or ""),
                    image=str(item.get("image") or ""),
                ))
            except (KeyError, TypeError, ValueError):
                log.warning("Skipping a menu entry that could not be read: %r", item)
        return foods

    def availability(self) -> dict[str, bool]:
        data = self._cached("availability", lambda: self._get_json(f"{self._inventory_url}/api/inventory/availability"), {})
        return {str(k): bool(v) for k, v in data.items()} if isinstance(data, dict) else {}

    def _get_json(self, url: str) -> object:
        response = self._client.get(url)
        response.raise_for_status()
        body = response.json()
        if not body.get("success", False):
            raise ValueError(f"{url} answered success=false")
        return body["data"]

    def _cached(self, key: str, load: Callable[[], object], empty: object) -> object:
        now = self._clock()
        with self._lock:
            hit = self._cache.get(key)
            if hit and now - hit[0] < self._ttl:
                return hit[1]
        try:
            value = load()
        except (httpx.HTTPError, ValueError, KeyError) as e:
            log.warning("Could not load %s (%s); using the last answer", key, e)
            return hit[1] if hit else empty  # stale is better than nothing
        with self._lock:
            self._cache[key] = (now, value)
        return value
