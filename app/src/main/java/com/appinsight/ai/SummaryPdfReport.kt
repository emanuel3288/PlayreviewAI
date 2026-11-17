package com.appinsight.ai

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.pdf.PdfDocument
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** A compact report of the active analysis shown on the Overview screen. */
object SummaryPdfReport {
    fun create(appTitle: String, marketName: String, insights: Insights, reviews: List<RealReview>, localeCode: String): ByteArray {
        require(insights.size > 0)
        val language = when (localeCode) {
            "EN" -> 1
            "PT" -> 2
            else -> 0
        }
        fun label(es: String, en: String, pt: String) = listOf(es, en, pt)[language]
        val locale = when (language) {
            1 -> Locale.ENGLISH
            2 -> Locale.forLanguageTag("pt-BR")
            else -> Locale.forLanguageTag("es-AR")
        }
        val report = Writer(label("Resumen Premium", "Premium overview", "Resumo Premium"), label("Página", "Page", "Página"))
        try {
            report.start(label("PlayReview · Resumen Premium", "PlayReview · Premium overview", "PlayReview · Resumo Premium"))
            report.paragraph(appTitle, 17f, report.ink, true)
            report.paragraph("${label("Tienda consultada", "Store queried", "Loja consultada")}: $marketName", 10f, report.muted)
            report.paragraph(SimpleDateFormat("dd MMM yyyy · HH:mm", locale).format(Date()), 9f, report.muted)
            report.space(14f)
            report.section(label("PANORAMA DE LA MUESTRA", "SAMPLE OVERVIEW", "VISÃO GERAL DA AMOSTRA"))
            report.metric("${"%.2f".format(locale, insights.average)} / 5", label("Calificación media de las reseñas analizadas", "Average rating of analyzed reviews", "Nota média das avaliações analisadas"))
            report.metric(insights.size.toString(), label("Reseñas analizadas", "Reviews analyzed", "Avaliações analisadas"))
            report.paragraph(label("Las cifras de este informe describen la muestra analizada, no la calificación pública total de la app en Google Play.", "The figures in this report describe the analyzed sample, not the app's overall public rating on Google Play.", "Os números deste relatório descrevem a amostra analisada, não a avaliação pública total do aplicativo no Google Play."), 9f, report.muted)

            report.space(12f)
            report.section(label("CLIMA DE LAS OPINIONES", "REVIEW SENTIMENT", "SENTIMENTO DAS AVALIAÇÕES"))
            val sentiments = listOf(
                Triple(label("Positivas", "Positive", "Positivas"), insights.sentiments["positive"] ?: 0, Color.rgb(34, 197, 94)),
                Triple(label("Neutrales", "Neutral", "Neutras"), insights.sentiments["neutral"] ?: 0, Color.rgb(245, 158, 11)),
                Triple(label("Negativas", "Negative", "Negativas"), insights.sentiments["negative"] ?: 0, Color.rgb(239, 68, 68)),
            )
            val sentimentMax = sentiments.maxOf { it.second }.coerceAtLeast(1)
            sentiments.forEach { (name, count, color) -> report.bar(name, count, sentimentMax, color) }

            report.space(12f)
            report.section(label("DISTRIBUCIÓN DE ESTRELLAS", "STAR DISTRIBUTION", "DISTRIBUIÇÃO DE ESTRELAS"))
            if (reviews.isEmpty()) {
                report.paragraph(label("Las reseñas individuales de este análisis no están disponibles; no se puede reconstruir la distribución.", "Individual reviews from this analysis are unavailable, so the distribution cannot be reconstructed.", "As avaliações individuais desta análise não estão disponíveis; não é possível reconstruir a distribuição."), 9f, report.muted)
            } else {
                val counts = (5 downTo 1).associateWith { star -> reviews.count { it.rating == star } }
                val maxCount = counts.values.maxOrNull()?.coerceAtLeast(1) ?: 1
                (5 downTo 1).forEach { star ->
                    val color = when (star) {
                        5 -> Color.rgb(34, 197, 94)
                        4 -> Color.rgb(132, 204, 22)
                        3 -> Color.rgb(245, 158, 11)
                        2 -> Color.rgb(251, 146, 60)
                        else -> Color.rgb(239, 68, 68)
                    }
                    report.bar("$star ★", counts[star] ?: 0, maxCount, color)
                }
            }

            report.space(12f)
            report.section(label("TENDENCIA DE CALIFICACIONES", "RATING TREND", "TENDÊNCIA DE AVALIAÇÕES"))
            report.trend(insights.weekTrend, label("Última semana", "Last week", "Última semana"), label("No hay datos semanales disponibles.", "No weekly data available.", "Não há dados semanais disponíveis."), language, locale)
            report.trend(insights.monthTrend, label("Último mes", "Last month", "Último mês"), label("No hay datos mensuales disponibles.", "No monthly data available.", "Não há dados mensais disponíveis."), language, locale)

            report.space(8f)
            report.paragraph(label("La tendencia muestra el promedio diario de las reseñas disponibles. Los cambios entre días dependen de cuántas reseñas se obtuvieron en cada fecha.", "The trend shows the daily average of available reviews. Day-to-day changes depend on how many reviews were retrieved for each date.", "A tendência mostra a média diária das avaliações disponíveis. As mudanças entre dias dependem de quantas avaliações foram obtidas em cada data."), 9f, report.muted)
            return report.bytes()
        } finally {
            report.close()
        }
    }

    private class Writer(private val continuationTitle: String, private val pageLabel: String) {
        private val pdf = PdfDocument()
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private var page: PdfDocument.Page? = null
        private var canvas: Canvas? = null
        private var pageNumber = 0
        private var y = 0f
        private val left = 44f
        private val right = 551f
        private val bottom = 790f
        val ink = Color.rgb(32, 45, 68)
        val muted = Color.rgb(102, 116, 137)
        private val blue = Color.rgb(38, 99, 235)

        private fun style(size: Float, color: Int, bold: Boolean = false) {
            paint.color = color
            paint.textSize = size
            paint.typeface = android.graphics.Typeface.create("sans-serif", if (bold) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
            paint.style = Paint.Style.FILL
        }

        private fun finishPage() {
            val current = page ?: return
            style(9f, muted)
            current.canvas.drawText("PlayReview · $pageLabel $pageNumber", left, 817f, paint)
            pdf.finishPage(current)
            page = null
            canvas = null
        }

        fun start(title: String) {
            finishPage()
            pageNumber++
            page = pdf.startPage(PdfDocument.PageInfo.Builder(595, 842, pageNumber).create())
            canvas = page!!.canvas
            y = 52f
            style(19f, blue, true)
            canvas!!.drawText(title, left, y, paint)
            y += 19f
            paint.color = Color.rgb(215, 224, 238)
            canvas!!.drawRect(left, y, right, y + 1f, paint)
            y += 20f
        }

        private fun ensure(height: Float) {
            if (y + height > bottom) start(continuationTitle)
        }

        fun space(height: Float) { ensure(height); y += height }

        fun paragraph(value: String, size: Float = 10f, color: Int = ink, bold: Boolean = false) {
            var remaining = value.replace('\n', ' ').replace('\r', ' ').trim()
            while (remaining.isNotEmpty()) {
                ensure(size + 8f)
                style(size, color, bold)
                val fit = paint.breakText(remaining, true, right - left, null).coerceAtLeast(1)
                val cut = if (fit < remaining.length) remaining.lastIndexOf(' ', fit).takeIf { it > 0 } ?: fit else fit
                val chunk = remaining.take(cut).trim()
                if (chunk.isNotEmpty()) {
                    canvas!!.drawText(chunk, left, y, paint)
                    y += size + 5f
                }
                remaining = remaining.drop(cut).trimStart()
            }
        }

        fun section(title: String) {
            ensure(25f)
            paragraph(title, 10f, blue, true)
            y += 5f
        }

        fun metric(value: String, caption: String) {
            ensure(40f)
            style(16f, ink, true)
            canvas!!.drawText(value, left, y, paint)
            y += 17f
            paragraph(caption, 9f, muted)
            y += 3f
        }

        fun bar(title: String, count: Int, maximum: Int, color: Int) {
            ensure(27f)
            style(10f, ink, true)
            canvas!!.drawText(title, left, y, paint)
            val top = y - 8f
            paint.color = Color.rgb(229, 235, 244)
            canvas!!.drawRoundRect(170f, top, 480f, top + 9f, 4f, 4f, paint)
            if (count > 0) {
                paint.color = color
                canvas!!.drawRoundRect(170f, top, 170f + 310f * count / maximum, top + 9f, 4f, 4f, paint)
            }
            style(10f, ink)
            canvas!!.drawText(count.toString(), 490f, y, paint)
            y += 24f
        }

        fun trend(trend: RatingTrend?, title: String, unavailable: String, language: Int, locale: Locale) {
            ensure(190f)
            paragraph(title, 12f, ink, true)
            if (trend == null) {
                paragraph(unavailable, 9f, muted)
                space(9f)
                return
            }
            val changeText = when (trend.direction) {
                "positive" -> listOf("Subieron", "Went up", "Subiram")[language]
                "negative" -> listOf("Bajaron", "Went down", "Caíram")[language]
                else -> listOf("Se mantuvieron", "Stayed steady", "Ficaram estáveis")[language]
            }
            paragraph("$changeText · ${"%+.2f".format(locale, trend.change)} ★ · ${trend.points.size} ${listOf("días con reseñas", "days with reviews", "dias com avaliações")[language]}", 9f, muted)
            if (trend.points.size < 2) {
                paragraph(unavailable, 9f, muted)
                space(9f)
                return
            }
            val top = y + 8f
            val chartBottom = top + 105f
            paint.color = Color.rgb(229, 235, 244)
            repeat(5) { step ->
                val guide = top + step * (chartBottom - top) / 4f
                canvas!!.drawLine(80f, guide, right, guide, paint)
            }
            val path = Path()
            trend.points.forEachIndexed { index, point ->
                val x = 80f + (right - 80f) * index / (trend.points.size - 1)
                val score = point.average.toFloat().coerceIn(1f, 5f)
                val yy = chartBottom - (score - 1f) / 4f * (chartBottom - top)
                if (index == 0) path.moveTo(x, yy) else path.lineTo(x, yy)
            }
            paint.color = blue
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 2.5f
            canvas!!.drawPath(path, paint)
            paint.style = Paint.Style.FILL
            y = chartBottom + 16f
            paragraph("${trend.points.first().day}   →   ${trend.points.last().day}", 9f, muted)
            space(12f)
        }

        fun bytes(): ByteArray {
            finishPage()
            return ByteArrayOutputStream().use { output -> pdf.writeTo(output); output.toByteArray() }
        }

        fun close() { pdf.close() }
    }
}
