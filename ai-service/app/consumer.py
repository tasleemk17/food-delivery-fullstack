"""
Reads the order events from Kafka and feeds the recommender.

Same rules as the other consumers in this project:
- the offset is committed only after the message is handled (at-least-once delivery; the recommender
  ignores a repeated event id, so a repeat is harmless)
- a message that cannot be parsed goes straight to the dead letter topic (retrying cannot help)
- a message that fails for another reason (for example the database is down) is retried with a growing
  pause, and goes to the dead letter topic only when the retries are used up, so one bad message
  never blocks the topic forever
"""
from __future__ import annotations

import logging
import threading
import time
from typing import Any, Callable

from .config import Settings
from .events import InvalidEvent, parse_order_event
from .recommender import RecommendationStore

log = logging.getLogger(__name__)


class EventHandler:
    def __init__(self, store: RecommendationStore) -> None:
        self._store = store

    def handle(self, raw: bytes | str) -> bool:
        """Raises InvalidEvent for a message that is not an order event. Returns True if it was counted."""
        return self._store.record(parse_order_event(raw))


class KafkaWorker:
    def __init__(self, settings: Settings, handler: EventHandler, consumer: Any | None = None,
                 producer: Any | None = None, sleep: Callable[[float], None] = time.sleep) -> None:
        self._settings = settings
        self._handler = handler
        self._consumer = consumer
        self._producer = producer
        self._sleep = sleep
        self._stop = threading.Event()
        self._thread: threading.Thread | None = None
        self.dead_letter_topic = f"{settings.kafka_topic}.ai.DLT"

    # ---- lifecycle -------------------------------------------------------------------------

    def start(self) -> None:
        from confluent_kafka import Consumer, Producer  # imported here so tests need no broker library setup

        s = self._settings
        if self._consumer is None:
            self._consumer = Consumer({
                "bootstrap.servers": s.kafka_bootstrap_servers,
                "group.id": s.kafka_group_id,
                "enable.auto.commit": False,
                "auto.offset.reset": "earliest",  # a fresh database learns from the whole history
            })
        if self._producer is None:
            self._producer = Producer({"bootstrap.servers": s.kafka_bootstrap_servers})
        self._consumer.subscribe([s.kafka_topic])
        self._thread = threading.Thread(target=self._run, name="kafka-worker", daemon=True)
        self._thread.start()

    def stop(self) -> None:
        self._stop.set()
        if self._thread:
            self._thread.join(timeout=10)
        if self._consumer is not None:
            try:
                self._consumer.close()
            except Exception:  # shutting down: nothing useful to do about it
                log.debug("Closing the consumer failed", exc_info=True)

    def _run(self) -> None:
        log.info("Listening to %s as group %s", self._settings.kafka_topic, self._settings.kafka_group_id)
        while not self._stop.is_set():
            try:
                message = self._consumer.poll(1.0)
            except Exception:
                log.exception("Polling Kafka failed")
                self._sleep(2)
                continue
            if message is None:
                continue
            if message.error():
                log.warning("Kafka error: %s", message.error())
                continue
            self.process(message.key(), message.value())
            try:
                self._consumer.commit(message=message, asynchronous=False)
            except Exception:
                # Not committed: the message comes again after a restart, and the repeat is ignored
                log.exception("Could not commit the offset")

    # ---- one message -----------------------------------------------------------------------

    def process(self, key: bytes | None, value: bytes | None) -> None:
        """Handles one message completely: counted, ignored, or sent to the dead letter topic."""
        raw = value or b""
        delay = self._settings.kafka_retry_initial_seconds
        attempts = max(1, self._settings.kafka_max_retries)
        for attempt in range(1, attempts + 1):
            if self._stop.is_set():
                return
            try:
                self._handler.handle(raw)
                return
            except InvalidEvent as e:
                self._dead_letter(key, raw, f"invalid: {e}")
                return
            except Exception as e:
                log.warning("Attempt %d/%d failed: %s", attempt, attempts, e)
                if attempt == attempts:
                    self._dead_letter(key, raw, f"failed after {attempts} attempts: {e}")
                    return
                self._sleep(delay)
                delay = min(delay * 2, self._settings.kafka_retry_max_seconds)

    def _dead_letter(self, key: bytes | None, raw: bytes, reason: str) -> None:
        log.error("Sending a message to %s (%s)", self.dead_letter_topic, reason)
        try:
            self._producer.produce(self.dead_letter_topic, key=key, value=raw,
                                   headers=[("error", reason[:500].encode("utf-8"))])
            self._producer.flush(10)
        except Exception:
            log.exception("Could not write to the dead letter topic")
