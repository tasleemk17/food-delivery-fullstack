"""
The one place that talks to Claude.

Written against the official Anthropic SDK. Three things worth knowing:
- Thinking cannot be turned off on this model, and thinking tokens count towards max_tokens, so
  max_tokens is generous (the answer itself is short) and output_config.effort is "low": a menu
  question needs no deep reasoning, and low effort keeps it quick and cheap.
- Sampling settings such as temperature are not sent: this model rejects them.
- Safety classifiers can decline a request (stop_reason "refusal"). Where the model supports it, the
  server-side "default" fallback is switched on, so the API re-runs the request on a suitable model.
  If it still refuses, the caller gets LLMUnavailable and answers from search instead.
"""
from __future__ import annotations

import logging
from typing import Any, Protocol

import anthropic

from .config import FALLBACK_MODELS, Settings

log = logging.getLogger(__name__)

_FALLBACK_BETA = "server-side-fallback-2026-07-01"


class LLMUnavailable(Exception):
    """Claude could not give an answer (no network, an error, a refusal, or an empty reply)."""


class LLM(Protocol):
    def complete(self, system: str, messages: list[dict[str, str]]) -> str: ...


class ClaudeLLM:
    def __init__(self, settings: Settings, client: Any | None = None) -> None:
        self._model = settings.anthropic_model
        self._max_tokens = settings.anthropic_max_tokens
        self._effort = settings.anthropic_effort
        # max_retries=1: the SDK retries a brief network hiccup once, but a chat user will not wait long
        self._client = client or anthropic.Anthropic(
            api_key=settings.anthropic_api_key,
            timeout=settings.anthropic_timeout_seconds,
            max_retries=1,
        )

    def complete(self, system: str, messages: list[dict[str, str]]) -> str:
        request: dict[str, Any] = dict(
            model=self._model,
            max_tokens=self._max_tokens,
            system=system,
            messages=messages,
            output_config={"effort": self._effort},
        )
        try:
            if self._model in FALLBACK_MODELS:
                response = self._client.beta.messages.create(betas=[_FALLBACK_BETA], fallbacks="default", **request)
            else:
                response = self._client.messages.create(**request)
        except anthropic.APIError as e:
            # Connection problems, timeouts, rate limits, bad key, server errors: all end up here
            log.warning("Claude request failed: %s (request id %s)", e, getattr(e, "request_id", None))
            raise LLMUnavailable(str(e)) from e

        # Always look at the stop reason before reading the content
        if response.stop_reason == "refusal":
            category = getattr(getattr(response, "stop_details", None), "category", None)
            log.warning("Claude declined the request (category %s)", category)
            raise LLMUnavailable("The request was declined")

        text = "".join(block.text for block in response.content if getattr(block, "type", None) == "text").strip()
        if not text:
            # For example the token limit was used up before any answer text
            log.warning("Claude returned no text (stop reason %s)", response.stop_reason)
            raise LLMUnavailable("Empty answer")
        return text
