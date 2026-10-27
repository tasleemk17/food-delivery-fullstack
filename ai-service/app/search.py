"""
A small BM25 search engine, written out here (no extra library) because it is only about 40 lines.

BM25 ranks documents by how well their words match the question: a rare word that a document
contains counts for more than a common one, and a short document is not beaten by a long one.
It is the classic keyword search that many real RAG systems still use, alone or next to
embeddings. It needs no model download and no GPU, and for a menu and a few help pages it is enough.
"""
from __future__ import annotations

import math
import re
from collections import Counter
from dataclasses import dataclass, field

# Words that say nothing about WHAT is asked (English, and the Hinglish ones people really type)
_STOPWORDS = frozenset(
    "a an the is are was were be have has had do does did can could would will i me my we you your it its of to in on at for "
    "and or with what which who how when where why please tell show any some there this that "
    "hai hain ka ki ke ko kya kaise kab kahan mein me se par aur ya mujhe mere hum tum aap"
    .split()
)
# Latin letters and digits, or a run of Devanagari letters (Hindi)
_TOKEN = re.compile(r"[a-z0-9]+|[ऀ-ॿ]+")


def tokenize(text: str) -> list[str]:
    tokens = []
    for raw in _TOKEN.findall(text.lower()):
        if raw in _STOPWORDS:
            continue
        # "noodles" and "noodle", "salads" and "salad" should match
        if len(raw) > 3 and raw.endswith("s") and not raw.endswith("ss"):
            raw = raw[:-1]
        tokens.append(raw)
    return tokens


@dataclass(frozen=True)
class Document:
    id: str
    title: str
    text: str
    kind: str  # "menu" or "help"
    meta: dict = field(default_factory=dict, compare=False, hash=False)


@dataclass(frozen=True)
class Hit:
    document: Document
    score: float


class BM25Index:
    def __init__(self, documents: list[Document], k1: float = 1.5, b: float = 0.75) -> None:
        self._documents = documents
        self._k1 = k1
        self._b = b
        self._tokens = [tokenize(f"{d.title} {d.text}") for d in documents]
        self._counts = [Counter(t) for t in self._tokens]
        self._lengths = [len(t) for t in self._tokens]
        self._avg_length = (sum(self._lengths) / len(self._lengths)) if self._lengths else 0.0
        document_frequency: Counter[str] = Counter()
        for counts in self._counts:
            document_frequency.update(counts.keys())
        n = len(documents)
        self._idf = {
            term: math.log(1 + (n - df + 0.5) / (df + 0.5)) for term, df in document_frequency.items()
        }

    def search(self, query: str, limit: int = 5, min_score: float = 0.0) -> list[Hit]:
        terms = tokenize(query)
        if not terms or not self._documents or limit <= 0:
            return []
        hits: list[Hit] = []
        for index, counts in enumerate(self._counts):
            score = 0.0
            for term in terms:
                frequency = counts.get(term, 0)
                if frequency == 0:
                    continue
                length_norm = 1 - self._b + self._b * (self._lengths[index] / self._avg_length if self._avg_length else 1)
                score += self._idf.get(term, 0.0) * (frequency * (self._k1 + 1)) / (frequency + self._k1 * length_norm)
            if score > min_score:
                hits.append(Hit(self._documents[index], score))
        hits.sort(key=lambda h: (-h.score, h.document.id))
        return hits[:limit]
