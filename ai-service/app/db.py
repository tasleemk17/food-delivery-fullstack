"""PostgreSQL connection pool and a tiny migration runner (numbered .sql files, each applied once)."""
from __future__ import annotations

import logging
from pathlib import Path

from psycopg_pool import ConnectionPool

log = logging.getLogger(__name__)

MIGRATIONS_DIR = Path(__file__).parent / "migrations"
# Any number; makes two instances that start at the same moment take turns instead of clashing
_MIGRATION_LOCK_ID = 727_001


def create_pool(database_url: str) -> ConnectionPool:
    # open=False + wait: fail at start-up with a clear error if the database is not reachable
    pool = ConnectionPool(database_url, min_size=1, max_size=5, open=False, timeout=10)
    pool.open(wait=True, timeout=30)
    return pool


def migrate(pool: ConnectionPool) -> list[str]:
    """Applies every migration file that has not been applied yet. Returns the names it applied."""
    applied: list[str] = []
    with pool.connection() as conn:
        conn.execute("SELECT pg_advisory_xact_lock(%s)", (_MIGRATION_LOCK_ID,))
        conn.execute(
            "CREATE TABLE IF NOT EXISTS schema_migrations ("
            "version TEXT PRIMARY KEY, applied_at TIMESTAMPTZ NOT NULL DEFAULT now())"
        )
        done = {row[0] for row in conn.execute("SELECT version FROM schema_migrations")}
        for path in sorted(MIGRATIONS_DIR.glob("*.sql")):
            if path.name in done:
                continue
            log.info("Applying migration %s", path.name)
            conn.execute(path.read_text(encoding="utf-8"))
            conn.execute("INSERT INTO schema_migrations (version) VALUES (%s)", (path.name,))
            applied.append(path.name)
    return applied
