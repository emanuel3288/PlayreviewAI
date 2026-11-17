from collections import Counter

KEYWORDS = {
    "inicio de sesión": ("login", "iniciar sesión", "contraseña", "cuenta"),
    "rendimiento": ("lenta", "lent", "congela", "lag"),
    "cierres inesperados": ("cierra", "crash", "se sale", "reinicia"),
    "notificaciones": ("notificación", "notificaciones"),
    "conectividad": ("conexión", "internet", "conecta"),
    "pagos": ("pago", "cobro", "tarjeta"),
}
def analyse_reviews(reviews):
    """Explainable local baseline; a provider adapter can enrich it later."""
    sentiments = Counter(); categories = Counter()
    for review in reviews:
        score = review.score
        sentiments["negative" if score <= 2 else "positive" if score >= 4 else "neutral"] += 1
        text = (review.content or "").lower()
        for category, words in KEYWORDS.items():
            if any(word in text for word in words): categories[category] += 1
    return {"sample_size": sum(sentiments.values()), "sentiments": dict(sentiments), "top_categories": categories.most_common(8), "method": "rule-based baseline; no technical cause is asserted"}
