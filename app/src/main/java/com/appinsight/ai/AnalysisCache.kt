package com.appinsight.ai

import android.content.Context
import android.util.AtomicFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileNotFoundException
import java.security.MessageDigest

/** Private, per-Firebase-user snapshots of completed analyses for offline reading. */
data class SavedAnalysis(
    val app: PlayApp,
    val insights: Insights,
    val reviews: List<RealReview>,
    val marketCountry: String,
    val maxReviews: Int,
    val reviewLanguages: List<String>?,
    val savedAt: Long,
)

object AnalysisCache {
    private const val maxSaved = 12
    private val lock = Any()

    suspend fun load(context: Context, uid: String): List<SavedAnalysis> = withContext(Dispatchers.IO) {
        synchronized(lock) { read(context, uid) }
    }

    suspend fun save(context: Context, uid: String, analysis: SavedAnalysis): List<SavedAnalysis> = withContext(Dispatchers.IO) {
        synchronized(lock) {
            val existing = read(context, uid)
            val updated = (listOf(analysis) + existing).take(maxSaved)
            val file = AtomicFile(fileFor(context, uid))
            file.baseFile.parentFile?.mkdirs()
            val bytes = JSONObject()
                .put("version", 1)
                .put("analyses", JSONArray().also { array -> updated.forEach { array.put(it.toJson()) } })
                .toString().toByteArray(Charsets.UTF_8)
            val output = file.startWrite()
            try {
                output.write(bytes)
                file.finishWrite(output)
            } catch (error: Exception) {
                file.failWrite(output)
                throw error
            }
            updated
        }
    }

    suspend fun clear(context: Context, uid: String) = withContext(Dispatchers.IO) {
        synchronized(lock) { fileFor(context, uid).delete() }
    }

    private fun fileFor(context: Context, uid: String): File {
        val hash = MessageDigest.getInstance("SHA-256").digest(uid.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        return File(File(context.filesDir, "analysis-cache"), "$hash.json")
    }

    private fun read(context: Context, uid: String): List<SavedAnalysis> {
        val root = try {
            JSONObject(AtomicFile(fileFor(context, uid)).openRead().bufferedReader().use { it.readText() })
        } catch (_: FileNotFoundException) {
            return emptyList()
        }
        if (root.optInt("version") != 1) return emptyList()
        val items = root.optJSONArray("analyses") ?: return emptyList()
        return (0 until minOf(items.length(), maxSaved)).mapNotNull { index ->
            runCatching { items.getJSONObject(index).toSavedAnalysis() }.getOrNull()
        }
    }

    private fun SavedAnalysis.toJson(): JSONObject = JSONObject()
        .put("app", JSONObject().put("id", app.id).put("title", app.title).put("developer", app.developer).put("icon", app.icon).put("score", app.score))
        .put("insights", JSONObject()
            .put("size", insights.size).put("average", insights.average)
            .put("sentiments", JSONObject(insights.sentiments))
            .put("categories", JSONArray().also { array -> insights.categories.forEach { (topic, count) -> array.put(JSONArray().put(topic).put(count)) } })
            .put("weekTrend", insights.weekTrend?.toJson())
            .put("twoWeekTrend", insights.twoWeekTrend?.toJson())
            .put("monthTrend", insights.monthTrend?.toJson()))
        .put("reviews", JSONArray().also { array -> reviews.forEach { review ->
            array.put(JSONObject().put("rating", review.rating).put("text", review.text).put("region", review.region).put("language", review.language).put("date", review.date))
        } })
        .put("marketCountry", marketCountry).put("maxReviews", maxReviews)
        .put("reviewLanguages", reviewLanguages?.let { JSONArray(it) })
        .put("savedAt", savedAt)

    private fun RatingTrend.toJson(): JSONObject = JSONObject()
        .put("days", days).put("average", average).put("change", change).put("direction", direction)
        .put("firstScore", firstScore).put("latestScore", latestScore).put("firstDay", firstDay).put("latestDay", latestDay)
        .put("points", JSONArray().also { array -> points.forEach { point ->
            array.put(JSONObject().put("day", point.day).put("average", point.average).put("count", point.count))
        } })

    private fun JSONObject.toSavedAnalysis(): SavedAnalysis {
        val appJson = getJSONObject("app")
        val app = PlayApp(appJson.getString("id"), appJson.getString("title"), appJson.stringOrNull("developer"), appJson.stringOrNull("icon"), appJson.doubleOrNull("score"))
        val insightsJson = getJSONObject("insights")
        val sentimentsJson = insightsJson.getJSONObject("sentiments")
        val sentiments = sentimentsJson.keys().asSequence().associateWith { sentimentsJson.getInt(it) }
        val categoriesJson = insightsJson.getJSONArray("categories")
        val categories = List(categoriesJson.length()) { index -> categoriesJson.getJSONArray(index).let { it.getString(0) to it.getInt(1) } }
        val insights = Insights(insightsJson.getInt("size"), insightsJson.getDouble("average"), sentiments, categories,
            insightsJson.optJSONObject("weekTrend")?.toRatingTrend(), insightsJson.optJSONObject("twoWeekTrend")?.toRatingTrend(), insightsJson.optJSONObject("monthTrend")?.toRatingTrend())
        val reviewsJson = getJSONArray("reviews")
        val reviews = List(reviewsJson.length()) { index ->
            reviewsJson.getJSONObject(index).let { review ->
                RealReview(review.getInt("rating"), review.getString("text"), review.getString("region"), review.getString("language"), review.stringOrNull("date"))
            }
        }
        val languages = optJSONArray("reviewLanguages")?.let { array -> List(array.length()) { array.getString(it) } }
        return SavedAnalysis(app, insights, reviews, getString("marketCountry"), getInt("maxReviews"), languages, getLong("savedAt"))
    }

    private fun JSONObject.toRatingTrend(): RatingTrend {
        val array = getJSONArray("points")
        val points = List(array.length()) { index -> array.getJSONObject(index).let { TrendPoint(it.getString("day"), it.getDouble("average"), it.getInt("count")) } }
        return RatingTrend(getInt("days"), getDouble("average"), getDouble("change"), getString("direction"),
            doubleOrNull("firstScore"), doubleOrNull("latestScore"), stringOrNull("firstDay"), stringOrNull("latestDay"), points)
    }

    private fun JSONObject.stringOrNull(key: String): String? = if (isNull(key)) null else optString(key).ifBlank { null }
    private fun JSONObject.doubleOrNull(key: String): Double? = if (isNull(key)) null else getDouble(key)
}
