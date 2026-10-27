from app.knowledge import KnowledgeBase, load_chunks
from app.search import BM25Index, Document, tokenize


def docs():
    return [
        Document("1", "Creamy Pasta", "pasta white sauce mushrooms", "menu"),
        Document("2", "Veg Noodles", "stir fried noodles vegetables", "menu"),
        Document("3", "Greek Salad", "tomatoes cucumber feta olives", "menu"),
    ]


def test_tokenize_drops_stopwords_and_plurals():
    assert tokenize("What are the Noodles, please?") == ["noodle"]
    assert tokenize("mujhe pasta chahiye") == ["pasta", "chahiye"]


def test_tokenize_keeps_hindi_words():
    assert "पनीर" in tokenize("पनीर क्या है")


def test_best_match_comes_first():
    hits = BM25Index(docs()).search("do you have noodles")
    assert hits[0].document.id == "2"


def test_no_match_gives_nothing():
    assert BM25Index(docs()).search("burger") == []
    assert BM25Index(docs()).search("the a is") == []
    assert BM25Index([]).search("pasta") == []


def test_limit_is_respected():
    assert len(BM25Index(docs()).search("pasta noodles salad", limit=2)) == 2


def test_help_articles_are_split_by_heading():
    chunks = load_chunks()
    assert len(chunks) > 8
    assert all(c.kind == "help" and c.text for c in chunks)
    assert len({c.id for c in chunks}) == len(chunks)


def test_the_right_help_chunk_is_found():
    kb = KnowledgeBase()
    assert "payments" in kb.search("can I pay with a card on delivery")[0].document.id.lower()
    assert kb.search("what is the delivery fee")[0].document.text.count("50") >= 1


def test_nonsense_finds_no_help():
    assert KnowledgeBase().search("xyzzy qwerty") == []


def test_common_verbs_do_not_cause_matches():
    index = BM25Index([Document("1", "Chicken Salad", "I have never eaten a salad like this", "menu")])
    assert index.search("do you have sandwich") == []
