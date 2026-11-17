package com.appinsight.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class PlayApp(val id: String, val title: String, val developer: String?, val icon: String?, val score: Double?)
data class ScrapeJob(val id: String, val status: String, val found: Int, val new: Int, val doneRegions: Int, val totalRegions: Int, val error: String?, val creditsRemaining: Int? = null)
data class RealReview(val rating: Int, val text: String, val region: String, val language: String, val date: String?)
data class TrendPoint(val day: String, val average: Double, val count: Int)
data class RatingTrend(val days: Int, val average: Double, val change: Double, val direction: String, val firstScore: Double?, val latestScore: Double?, val firstDay: String?, val latestDay: String?, val points: List<TrendPoint>)
data class Insights(val size: Int, val average: Double, val sentiments: Map<String, Int>, val categories: List<Pair<String, Int>>, val weekTrend: RatingTrend?, val twoWeekTrend: RatingTrend?, val monthTrend: RatingTrend?)
data class AnalysisResult(val job: ScrapeJob, val insights: Insights, val reviews: List<RealReview>)
data class MarketComparison(val country: String, val language: String, val available: Boolean, val score: Double?, val scoreText: String?, val ratings: Long, val histogram: Map<Int, Long>, val storeUrl: String?, val error: String?, val insights: Insights?, val reviews: List<RealReview>, val reviewError: String?, val sharedReviewCount: Int)
data class ComparisonResult(val markets: List<MarketComparison>)
data class UserPlan(val name: String, val dailyLimit: Int, val maxReviews: Int, val creditsRemaining: Int, val creditsLimit: Int)
data class PortfolioRun(val date: String, val periodDays: Int, val reviewCount: Int, val average: Double, val negativeCount: Int)
data class PortfolioApp(val id: String, val title: String, val developer: String?, val runs: List<PortfolioRun>)
class ApiClient(private val rawBaseUrl: String, private val accessToken: suspend () -> String? = { null }) {
    private val baseUrl = rawBaseUrl.trim().trimEnd('/')
    private fun JSONObject.optionalString(name: String): String? =
        if (isNull(name)) null else optString(name).trim().takeIf { it.isNotEmpty() && it != "null" }

    private suspend fun request(path: String, method: String = "GET", body: String? = null): String = withContext(Dispatchers.IO) {
        val connection = (URL("$baseUrl$path").openConnection() as HttpURLConnection).apply {
            requestMethod = method; connectTimeout = 15_000; readTimeout = 60_000
            setRequestProperty("Accept", "application/json")
            accessToken()?.let { setRequestProperty("Authorization", "Bearer $it") }
            if (body != null) { doOutput = true; setRequestProperty("Content-Type", "application/json"); outputStream.use { it.write(body.toByteArray()) } }
        }
        val stream = if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream
        val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
        if (connection.responseCode !in 200..299) throw IllegalStateException(JSONObject(text.ifBlank { "{}" }).optString("detail", "Error HTTP ${connection.responseCode}"))
        text
    }

    suspend fun search(query: String, country: String, language: String): List<PlayApp> {
        val encodedQuery = java.net.URLEncoder.encode(query, "UTF-8")
        val json = JSONArray(request("/api/apps/search?q=$encodedQuery&country=$country&lang=$language"))
        return List(json.length()) { i -> json.getJSONObject(i).let { PlayApp(it.getString("app_id"), it.getString("title"), it.optString("developer").ifBlank { null }, it.optString("icon").ifBlank { null }, if (it.isNull("score")) null else it.getDouble("score")) } }
    }

    suspend fun profile(): UserPlan {
        val json = JSONObject(request("/api/profile"))
        return UserPlan(json.getString("plan"), json.getInt("daily_limit"), json.getInt("max_reviews"), json.optInt("credits_remaining", json.getInt("daily_limit")), json.optInt("credits_limit", json.getInt("daily_limit")))
    }

    suspend fun deleteAccount() {
        request("/api/account", "DELETE")
    }

    suspend fun portfolio(): List<PortfolioApp> {
        val apps = JSONObject(request("/api/portfolio")).getJSONArray("apps")
        return List(apps.length()) { index ->
            val item = apps.getJSONObject(index)
            val runs = item.getJSONArray("runs")
            PortfolioApp(
                item.getString("app_id"), item.getString("title"), item.optString("developer").ifBlank { null },
                List(runs.length()) { runIndex ->
                    val run = runs.getJSONObject(runIndex)
                    PortfolioRun(run.getString("date"), run.getInt("period_days"), run.getInt("review_count"), run.getDouble("average_score"), run.getInt("negative_count"))
                }
            )
        }
    }

    suspend fun analyze(app: PlayApp, periodDays: Int, country: String, language: String, maxReviews: Int = 50, reviewLanguages: List<String>? = null): AnalysisResult {
        val body = JSONObject().put("app_id", app.id).put("app_title", app.title)
            .put("app_developer", app.developer).put("app_icon", app.icon)
            .put("period_days", periodDays).put("max_reviews", maxReviews)
            .put("country", country).put("language", language)
        if (reviewLanguages != null) body.put("review_languages", JSONArray(reviewLanguages))
        var queued = JSONObject(request("/api/analyze", "POST", body.toString()))
        var attempts = 0
        while (!queued.has("result")) {
            val id = queued.optString("id")
            if (id.isBlank()) throw IllegalStateException(queued.optString("detail", "No se pudo iniciar el análisis."))
            if (++attempts > 45) throw IllegalStateException("El análisis sigue en cola. Volvé a intentarlo en unos instantes.")
            delay(2_000)
            queued = JSONObject(request("/api/analysis-jobs?id=${java.net.URLEncoder.encode(id, "UTF-8")}"))
        }
        val json = queued.getJSONObject("result")
        val insight = parseInsights(json.getJSONObject("insights"), json.optJSONObject("trends"))
        val reviews = json.getJSONArray("reviews").let { array -> List(array.length()) { i -> array.getJSONObject(i).let { RealReview(it.getInt("score"), it.optString("content", "Sin comentario"), it.getString("region"), it.getString("language"), it.optString("date").ifBlank { null }) } } }
        return AnalysisResult(parseJob(json.getJSONObject("job")), insight, reviews)
    }

    suspend fun compare(app: PlayApp, countries: List<String>): ComparisonResult {
        val body = JSONObject().put("app_id", app.id).put("countries", JSONArray(countries))
        val json = JSONObject(request("/api/compare", "POST", body.toString()))
        val markets = json.getJSONArray("markets").let { array ->
            List(array.length()) { index ->
                val item = array.getJSONObject(index)
                val histogramJson = item.optJSONObject("histogram")
                val histogram = (1..5).associateWith { star -> histogramJson?.optLong(star.toString(), 0L) ?: 0L }
                val insights = item.optJSONObject("insights")?.let { parseInsights(it, item.optJSONObject("trends")) }
                val reviews = item.optJSONArray("reviews")?.let { reviewsJson ->
                    List(reviewsJson.length()) { reviewIndex ->
                        reviewsJson.getJSONObject(reviewIndex).let { review ->
                            RealReview(review.optInt("score"), review.optString("content", "Sin comentario"), review.optString("region", "und"), review.optString("language", "und"), review.optionalString("date"))
                        }
                    }
                } ?: emptyList()
                MarketComparison(
                    item.getString("country"), item.getString("language"), item.optBoolean("available"),
                    if (item.isNull("score")) null else item.optDouble("score"),
                    item.optionalString("score_text"),
                    item.optLong("ratings", 0L), histogram,
                    item.optionalString("store_url"),
                    item.optionalString("error"), insights, reviews,
                    item.optionalString("review_error"), item.optInt("shared_review_count", 0)
                )
            }
        }
        return ComparisonResult(markets)
    }

    private fun parseInsights(json: JSONObject, trends: JSONObject?): Insights {
        val sentimentJson = json.getJSONObject("sentiments")
        val sentiments = sentimentJson.keys().asSequence().associateWith { sentimentJson.getInt(it) }
        val categories = json.getJSONArray("top_categories").let { array -> List(array.length()) { i -> array.getJSONArray(i).getString(0) to array.getJSONArray(i).getInt(1) } }
        return Insights(
            json.getInt("sample_size"), json.getDouble("average_score"), sentiments, categories,
            parseTrend(trends?.optJSONObject("week")), parseTrend(trends?.optJSONObject("two_week")), parseTrend(trends?.optJSONObject("month"))
        )
    }

    private fun parseTrend(json: JSONObject?): RatingTrend? {
        if (json == null) return null
        val points = json.getJSONArray("points").let { array ->
            List(array.length()) { index ->
                array.getJSONObject(index).let { point ->
                    TrendPoint(point.getString("day"), point.getDouble("average_score"), point.getInt("count"))
                }
            }
        }
        return RatingTrend(
            json.getInt("days"), json.getDouble("average_score"), json.getDouble("change"),
            json.getString("direction"),
            if (json.isNull("first_score")) null else json.getDouble("first_score"),
            if (json.isNull("latest_score")) null else json.getDouble("latest_score"),
            json.optionalString("first_day"), json.optionalString("latest_day"), points
        )
    }

    private fun parseJob(json: JSONObject) = ScrapeJob(json.getString("id"), json.getString("status"), json.getInt("reviews_found"), json.getInt("reviews_new"), json.getJSONArray("completed_regions").length(), json.getJSONArray("requested_regions").length(), json.optString("error").ifBlank { null }, json.optInt("analyses_remaining", -1).takeIf { it >= 0 })
}
