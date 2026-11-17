from types import SimpleNamespace
from app.analysis import analyse_reviews
def test_analysis_uses_real_review_fields():
    result = analyse_reviews([SimpleNamespace(score=1, content="La app se cierra al iniciar sesión"), SimpleNamespace(score=5, content="Excelente")])
    assert result["sample_size"] == 2
    assert result["sentiments"]["negative"] == 1
    assert any(category == "cierres inesperados" for category, _ in result["top_categories"])
