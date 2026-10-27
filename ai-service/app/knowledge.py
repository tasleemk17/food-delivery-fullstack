"""
The help articles the chatbot can quote (markdown files in app/help_articles/).

Each file is cut into pieces at its "## " headings, so one question about "refunds" retrieves
the refund paragraph and not the whole file. A piece is called a "chunk".
"""
from __future__ import annotations

from pathlib import Path

from .search import BM25Index, Document, Hit

KNOWLEDGE_DIR = Path(__file__).parent / "help_articles"


def load_chunks(directory: Path = KNOWLEDGE_DIR) -> list[Document]:
    chunks: list[Document] = []
    for path in sorted(directory.glob("*.md")):
        article = path.stem
        article_title = path.stem.title()
        heading: str | None = None
        lines: list[str] = []

        def flush() -> None:
            body = " ".join(line.strip() for line in lines if line.strip())
            if heading and body:
                chunks.append(Document(
                    id=f"{article}#{len(chunks)}",
                    title=f"{article_title}: {heading}",
                    text=body,
                    kind="help",
                ))

        for line in path.read_text(encoding="utf-8").splitlines():
            if line.startswith("# "):
                article_title = line[2:].strip()
            elif line.startswith("## "):
                flush()
                heading = line[3:].strip()
                lines = []
            else:
                lines.append(line)
        flush()
    return chunks


class KnowledgeBase:
    def __init__(self, chunks: list[Document] | None = None) -> None:
        self._chunks = chunks if chunks is not None else load_chunks()
        self._index = BM25Index(self._chunks)

    @property
    def chunks(self) -> list[Document]:
        return self._chunks

    def search(self, query: str, limit: int = 3, min_score: float = 0.5) -> list[Hit]:
        """min_score: a weak, accidental match (one common word) should not be shown as an "answer"."""
        return self._index.search(query, limit=limit, min_score=min_score)
