"""
The HTTP API of the AI service. It is reached only through the gateway, which checks the JWT and sets
the X-User-Id header; the recommendation endpoints are public, the chat needs a logged in user.
"""
from __future__ import annotations

import logging
from contextlib import asynccontextmanager
from typing import Annotated

from fastapi import FastAPI, Header, Query
from fastapi.responses import JSONResponse
from pydantic import BaseModel, Field

from .catalog import Catalog, Food, HttpCatalog
from .chat import ChatService, Turn
from .config import Settings
from .consumer import EventHandler, KafkaWorker
from .db import create_pool, migrate
from .knowledge import KnowledgeBase
from .llm import ClaudeLLM
from .ratelimit import SlidingWindowLimiter
from .recommender import RecommendationStore, Scored

log = logging.getLogger(__name__)

MAX_RECOMMENDATIONS = 12


class HistoryTurn(BaseModel):
    role: str = Field(max_length=16)
    content: str = Field(max_length=2000)


class ChatRequest(BaseModel):
    message: str
    history: list[HistoryTurn] = Field(default_factory=list, max_length=50)


def _food_json(food: Food, score: int) -> dict:
    return {"_id": food.id, "name": food.name, "description": food.description, "price": food.price,
            "category": food.category, "image": food.image, "score": score}


def _error(status: int, message: str, headers: dict[str, str] | None = None) -> JSONResponse:
    return JSONResponse({"success": False, "message": message}, status_code=status, headers=headers)


def create_app(settings: Settings, store: RecommendationStore, catalog: Catalog, chat: ChatService,
               limiter: SlidingWindowLimiter, worker: KafkaWorker | None = None) -> FastAPI:

    @asynccontextmanager
    async def lifespan(_: FastAPI):
        if worker:
            worker.start()
        yield
        if worker:
            worker.stop()

    app = FastAPI(title="Tomato AI service", lifespan=lifespan, docs_url=None, redoc_url=None, openapi_url=None)

    def orderable(scored: list[Scored], exclude: set[str], limit: int) -> list[dict]:
        """Turns ids into menu entries; dishes that were removed from the menu or are sold out are dropped."""
        foods = {f.id: f for f in catalog.foods()}
        availability = catalog.availability()
        result = []
        for item in scored:
            food = foods.get(item.food_id)
            if food is None or item.food_id in exclude or not availability.get(item.food_id, True):
                continue
            result.append(_food_json(food, item.score))
            if len(result) == limit:
                break
        return result

    @app.get("/health")
    def health():
        return {"status": "UP", "chatMode": chat.mode}

    @app.get("/api/ai/recommendations")
    def recommendations(food_ids: Annotated[str, Query(alias="foodIds", max_length=2000)] = "",
                        limit: Annotated[int, Query(ge=1, le=MAX_RECOMMENDATIONS)] = 4):
        """Dishes ordered together with the given ones (the cart); topped up with the popular ones."""
        given = [i for i in dict.fromkeys(p.strip() for p in food_ids.split(",")) if i][:50]
        # ask for more than needed: some may be sold out or off the menu
        related = store.related(given, limit * 3) if given else []
        picked = orderable(related, set(given), limit)
        basis = "together" if picked else "popular"
        if len(picked) < limit:
            have = {p["_id"] for p in picked}
            more = orderable(store.popular(limit * 3 + len(given)), set(given) | have, limit - len(picked))
            picked += more
        return {"success": True, "data": picked, "basis": basis if picked else "none"}

    @app.get("/api/ai/recommendations/popular")
    def popular(limit: Annotated[int, Query(ge=1, le=MAX_RECOMMENDATIONS)] = 4):
        return {"success": True, "data": orderable(store.popular(limit * 3), set(), limit)}

    @app.post("/api/ai/chat")
    def chat_endpoint(body: ChatRequest, user_id: Annotated[str | None, Header(alias="X-User-Id")] = None):
        if not user_id:
            return _error(401, "Please log in to use the assistant")
        message = body.message.strip()
        if not message:
            return _error(422, "Please type a question")
        if len(message) > settings.chat_max_message_chars:
            return _error(422, f"Please keep the question under {settings.chat_max_message_chars} characters")

        allowed, retry_after = limiter.check(user_id)
        if not allowed:
            return _error(429, "You have asked a lot of questions. Please try again in a few minutes.",
                          {"Retry-After": str(retry_after)})

        history = [Turn(t.role, t.content) for t in body.history]
        result = chat.answer(message, history)
        return {"success": True, "data": {
            "answer": result.answer,
            "mode": result.mode,
            "degraded": result.degraded,
            "sources": [{"kind": s.kind, "title": s.title} for s in result.sources],
        }}

    return app


def build() -> FastAPI:
    """Wires the real parts together. Started by uvicorn: `uvicorn app.main:build --factory`."""
    logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s: %(message)s")
    settings = Settings.from_env()
    pool = create_pool(settings.database_url)
    applied = migrate(pool)
    if applied:
        log.info("Applied migrations: %s", ", ".join(applied))

    store = RecommendationStore(pool)
    catalog = HttpCatalog(settings.catalog_url, settings.inventory_url,
                          settings.catalog_cache_seconds, settings.catalog_timeout_seconds)
    llm = ClaudeLLM(settings) if settings.anthropic_api_key else None
    if llm is None:
        log.warning("ANTHROPIC_API_KEY is not set: the chat answers from search only")
    chat = ChatService(catalog, KnowledgeBase(), llm, settings)
    limiter = SlidingWindowLimiter(settings.chat_rate_limit_messages, settings.chat_rate_limit_window_seconds)
    worker = KafkaWorker(settings, EventHandler(store)) if settings.kafka_enabled else None
    return create_app(settings, store, catalog, chat, limiter, worker)
