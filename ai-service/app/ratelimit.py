"""A per-user limit on chat messages. Every message costs money, so one person cannot run up the bill."""
from __future__ import annotations

import threading
import time
from collections import deque
from typing import Callable


class SlidingWindowLimiter:
    """At most `max_events` per `window_seconds` for each key (for example a user id)."""

    def __init__(self, max_events: int, window_seconds: float, clock: Callable[[], float] = time.monotonic) -> None:
        self._max = max_events
        self._window = window_seconds
        self._clock = clock
        self._events: dict[str, deque[float]] = {}
        self._lock = threading.Lock()

    def check(self, key: str) -> tuple[bool, int]:
        """Records the event if allowed. Returns (allowed, seconds_until_the_next_one_is_allowed)."""
        now = self._clock()
        with self._lock:
            if len(self._events) > 10_000:
                self._forget_idle(now)
            events = self._events.setdefault(key, deque())
            while events and now - events[0] >= self._window:
                events.popleft()
            if len(events) >= self._max:
                return False, max(1, int(self._window - (now - events[0])) + 1)
            events.append(now)
            return True, 0

    def _forget_idle(self, now: float) -> None:
        for key in [k for k, e in self._events.items() if not e or now - e[-1] >= self._window]:
            del self._events[key]
