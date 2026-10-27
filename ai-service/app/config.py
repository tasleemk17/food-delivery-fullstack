"""All settings come from environment variables, so the same image runs on a laptop and in the cloud."""
from __future__ import annotations

import os
from dataclasses import dataclass, field

# Models that accept the server-side refusal fallback (see ClaudeLLM)
FALLBACK_MODELS = {"claude-opus-5-5", "claude-opus-5", "claude-fable-5-1", "claude-sonnet-5-5"}


def _int(name: str, default: int) -> int:
    value = os.environ.get(name, "").strip()
    return int(value) if value else default


def _float(name: str, default: float) -> float:
    value = os.environ.get(name, "").strip()
    return float(value) if value else default


@dataclass(frozen=True)
class Settings:
    # PostgreSQL (its own database; nothing else reads or writes it)
    database_url: str = "postgresql://ai:ai@localhost:5435/ai"

    # Kafka: the order events feed the recommendations
    kafka_bootstrap_servers: str = "localhost:9092"
    kafka_topic: str = "order-events"
    kafka_group_id: str = "ai-service"
    kafka_enabled: bool = True
    # A message that keeps failing is retried this many times, then parked on <topic>.ai.DLT
    kafka_max_retries: int = 8
    kafka_retry_initial_seconds: float = 1.0
    kafka_retry_max_seconds: float = 30.0

    # The menu (Node backend, public endpoint) and the stock list (inventory-service, public endpoint)
    catalog_url: str = "http://localhost:4000"
    inventory_url: str = "http://localhost:8082"
    catalog_cache_seconds: float = 30.0
    catalog_timeout_seconds: float = 3.0

    # Claude. Without a key the chatbot still works, but only answers from search (no generated text).
    anthropic_api_key: str = field(default="", repr=False)
    anthropic_model: str = "claude-opus-5-5"
    anthropic_timeout_seconds: float = 25.0
    # Thinking tokens count towards this limit too, so it is well above the length of a chat answer
    anthropic_max_tokens: int = 4000
    anthropic_effort: str = "low"

    # Chat limits: every message costs money, so they are kept small
    chat_max_message_chars: int = 500
    chat_max_history_messages: int = 6
    chat_rate_limit_messages: int = 20
    chat_rate_limit_window_seconds: int = 600
    # Up to this many dishes the whole menu goes into the prompt; a bigger menu is searched instead
    chat_full_menu_limit: int = 80

    @classmethod
    def from_env(cls) -> "Settings":
        env = os.environ.get
        return cls(
            database_url=env("DATABASE_URL", cls.database_url),
            kafka_bootstrap_servers=env("KAFKA_BOOTSTRAP_SERVERS", cls.kafka_bootstrap_servers),
            kafka_topic=env("KAFKA_TOPIC", cls.kafka_topic),
            kafka_group_id=env("KAFKA_GROUP_ID", cls.kafka_group_id),
            kafka_enabled=env("KAFKA_ENABLED", "true").strip().lower() != "false",
            kafka_max_retries=_int("KAFKA_MAX_RETRIES", cls.kafka_max_retries),
            kafka_retry_initial_seconds=_float("KAFKA_RETRY_INITIAL_SECONDS", cls.kafka_retry_initial_seconds),
            kafka_retry_max_seconds=_float("KAFKA_RETRY_MAX_SECONDS", cls.kafka_retry_max_seconds),
            catalog_url=env("CATALOG_URL", cls.catalog_url).rstrip("/"),
            inventory_url=env("INVENTORY_URL", cls.inventory_url).rstrip("/"),
            catalog_cache_seconds=_float("CATALOG_CACHE_SECONDS", cls.catalog_cache_seconds),
            catalog_timeout_seconds=_float("CATALOG_TIMEOUT_SECONDS", cls.catalog_timeout_seconds),
            anthropic_api_key=env("ANTHROPIC_API_KEY", "").strip(),
            anthropic_model=env("ANTHROPIC_MODEL", cls.anthropic_model).strip() or cls.anthropic_model,
            anthropic_timeout_seconds=_float("ANTHROPIC_TIMEOUT_SECONDS", cls.anthropic_timeout_seconds),
            anthropic_max_tokens=_int("ANTHROPIC_MAX_TOKENS", cls.anthropic_max_tokens),
            anthropic_effort=env("ANTHROPIC_EFFORT", cls.anthropic_effort).strip() or cls.anthropic_effort,
            chat_max_message_chars=_int("CHAT_MAX_MESSAGE_CHARS", cls.chat_max_message_chars),
            chat_max_history_messages=_int("CHAT_MAX_HISTORY_MESSAGES", cls.chat_max_history_messages),
            chat_rate_limit_messages=_int("CHAT_RATE_LIMIT_MESSAGES", cls.chat_rate_limit_messages),
            chat_rate_limit_window_seconds=_int("CHAT_RATE_LIMIT_WINDOW_SECONDS", cls.chat_rate_limit_window_seconds),
            chat_full_menu_limit=_int("CHAT_FULL_MENU_LIMIT", cls.chat_full_menu_limit),
        )
