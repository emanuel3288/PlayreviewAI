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

/** A printable report built from the exact reviews visible in the selected view. */
object PdfReport {
    private const val width = 595
    private const val height = 842
    private const val left = 44f
    private const val right = 551f
    private const val bottom = 795f
    private val ink = Color.rgb(32, 45, 68)
    private val muted = Color.rgb(102, 116, 137)
    private val blue = Color.rgb(38, 99, 235)

    fun create(appTitle: String, reviews: List<RealReview>, analysisCount: Int, filters: String, locale: String): ByteArray {
        require(reviews.isNotEmpty() && reviews.size <= 500)
        val pdf = PdfDocument()
        var page: PdfDocument.Page? = null
        var canvas: Canvas? = null
        var y = 0f
        var pageNumber = 0
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val english = locale == "EN"
        val portuguese = locale == "PT"
        val reportLocale = if (english) Locale.ENGLISH else if (portuguese) Locale.forLanguageTag("pt-BR") else Locale.forLanguageTag("es-AR")
        fun label(es: String, en: String, pt: String) = if (english) en else if (portuguese) pt else es
        val generatedAt = SimpleDateFormat("dd MMM yyyy · HH:mm", reportLocale).format(Date())
        val average = reviews.map { it.rating }.average()
        val positive = reviews.count { it.rating >= 4 }
        val neutral = reviews.count { it.rating == 3 }
        val negative = reviews.count { it.rating <= 2 }
        var continuationTitle = label("Informe premium (continuación)", "Premium report (continued)", "Relatório premium (continuação)")
        fun style(size: Float, color: Int = ink, bold: Boolean = false) {
            paint.color = color
            paint.textSize = size
            paint.typeface = if (bold) android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD)
                else android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.NORMAL)
            paint.style = Paint.Style.FILL
        }
        fun line(text: String, size: Float = 10f, color: Int = ink, bold: Boolean = false, gap: Float = 5f) {
            style(size, color, bold)
            canvas!!.drawText(text, left, y, paint)
            y += size + gap
        }
        fun finishPage() {
            val current = page ?: return
            style(9f, muted)
            current.canvas.drawText("PlayReview · ${label("Página", "Page", "Página")} $pageNumber", left, 817f, paint)
            pdf.finishPage(current)
            page = null
            canvas = null
        }
        fun startPage(title: String) {
            finishPage()
            pageNumber++
            page = pdf.startPage(PdfDocument.PageInfo.Builder(width, height, pageNumber).create())
            canvas = page!!.canvas
            y = 54f
            line(title, 19f, blue, true, 13f)
            paint.color = Color.rgb(215, 224, 238)
            canvas!!.drawRect(left, y, right, y + 1f, paint)
            y += 19f
        }
        fun paragraph(text: String, size: Float = 10f, color: Int = ink) {
            style(size, color)
            val content = text.replace('\r', ' ').replace('\n', ' ').trim()
            var remaining = content
            while (remaining.isNotEmpty()) {
                style(size, color)
                val fit = paint.breakText(remaining, true, right - left, null).coerceAtLeast(1)
                val cut = if (fit < remaining.length) remaining.lastIndexOf(' ', fit).takeIf { it > 0 } ?: fit else fit
                val chunk = remaining.take(cut).trim()
                if (y + size + 5f > bottom) startPage(continuationTitle)
                if (chunk.isNotEmpty()) line(chunk, size, color, gap = 4f)
                remaining = remaining.drop(cut).trimStart()
            }
        }
        try {
            startPage(label("PlayReview · Informe Premium", "PlayReview · Premium Report", "PlayReview · Relatório Premium"))
            paragraph(appTitle, 15f, ink)
            line(generatedAt, 9f, muted)
            y += 12f
            line(label("ALCANCE DEL INFORME", "REPORT SCOPE", "ESCOPO DO RELATÓRIO"), 10f, blue, true)
            paragraph(filters, 10f)
            line(label("${reviews.size} de $analysisCount reseñas del análisis", "${reviews.size} of $analysisCount reviews in the analysis", "${reviews.size} de $analysisCount avaliações da análise"), 10f, muted)
            y += 13f
            line(label("RESUMEN EJECUTIVO", "EXECUTIVE SUMMARY", "RESUMO EXECUTIVO"), 10f, blue, true)
            line(label("Calificación media: ${"%.2f".format(Locale.US, average)} / 5", "Average rating: ${"%.2f".format(Locale.US, average)} / 5", "Avaliação média: ${"%.2f".format(Locale.US, average)} / 5"), 14f, ink, true)
            line(label("Positivas $positive · Neutrales $neutral · Negativas $negative", "Positive $positive · Neutral $neutral · Negative $negative", "Positivas $positive · Neutras $neutral · Negativas $negative"), 10f, muted)
            val dated = reviews.mapNotNull { it.date?.take(10)?.takeIf { date -> date.length == 10 } }.sorted()
            if (dated.isNotEmpty()) line(label("Período: ${dated.first()} a ${dated.last()}", "Period: ${dated.first()} to ${dated.last()}", "Período: ${dated.first()} a ${dated.last()}"), 10f, muted)

            y += 20f
            line(label("Distribución de estrellas", "Star distribution", "Distribuição de estrelas"), 13f, ink, true, 12f)
            val counts = (5 downTo 1).associateWith { rating -> reviews.count { it.rating == rating } }
            val maxCount = counts.values.maxOrNull()?.coerceAtLeast(1) ?: 1
            for (rating in 5 downTo 1) {
                val count = counts[rating] ?: 0
                line("$rating ★", 10f, ink, true, 2f)
                paint.color = Color.rgb(229, 235, 244)
                canvas!!.drawRoundRect(104f, y - 13f, 455f, y - 3f, 5f, 5f, paint)
                if (count > 0) {
                    paint.color = blue
                    canvas!!.drawRoundRect(104f, y - 13f, 104f + 351f * count / maxCount, y - 3f, 5f, 5f, paint)
                }
                style(10f, ink)
                canvas!!.drawText(count.toString(), 469f, y - 4f, paint)
                y += 12f
            }

            y += 12f
            line(label("Evolución diaria de la selección", "Daily trend in this selection", "Evolução diária da seleção"), 13f, ink, true, 10f)
            val daily = reviews.filter { it.date?.length ?: 0 >= 10 }.groupBy { it.date!!.take(10) }
                .toSortedMap().map { (day, items) -> day to items.map { it.rating }.average().toFloat() }
            if (daily.size >= 2) {
                val chartTop = y + 3f
                val chartBottom = chartTop + 130f
                paint.color = Color.rgb(230, 235, 243)
                for (step in 0..4) {
                    val guide = chartTop + step * (chartBottom - chartTop) / 4f
                    canvas!!.drawLine(80f, guide, right, guide, paint)
                }
                val path = Path()
                daily.forEachIndexed { index, (_, score) ->
                    val x = 80f + (right - 80f) * index / (daily.size - 1)
                    val yy = chartBottom - (score - 1f).coerceIn(0f, 4f) / 4f * (chartBottom - chartTop)
                    if (index == 0) path.moveTo(x, yy) else path.lineTo(x, yy)
                }
                paint.color = blue
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 2.5f
                canvas!!.drawPath(path, paint)
                paint.style = Paint.Style.FILL
                y = chartBottom + 18f
                line("${daily.first().first}   →   ${daily.last().first}", 9f, muted)
            } else {
                line(label("No hay fechas suficientes para mostrar la evolución.", "Not enough dates to show a trend.", "Não há datas suficientes para mostrar a evolução."), 10f, muted)
            }

            startPage(label("Hallazgos y próximos pasos", "Findings and next steps", "Descobertas e próximos passos"))
            paragraph(label("Los hallazgos se calculan únicamente con las reseñas seleccionadas. Una mención puede aparecer en más de un tema.", "Findings use only the selected reviews. A review may mention more than one topic.", "As descobertas usam apenas as avaliações selecionadas. Uma avaliação pode mencionar mais de um tema."), 10f, muted)
            y += 15f
            val topics = listOf("inicio de sesión", "rendimiento", "cierres inesperados", "notificaciones", "conectividad", "pagos")
            val rankedTopics = topics.map { topic -> topic to reviews.filter { it.rating <= 2 && reviewMatchesTopic(it.text, topic) } }
                .filter { it.second.isNotEmpty() }.sortedByDescending { it.second.size }.take(3)
            if (rankedTopics.isEmpty()) {
                line(label("No se detectaron problemas recurrentes", "No recurring issues detected", "Nenhum problema recorrente detectado"), 12f, ink, true)
                paragraph(label("En esta selección no hay reseñas negativas que mencionen los temas rastreados. Revisá las reseñas del anexo antes de sacar conclusiones.", "This selection has no negative reviews mentioning tracked topics. Review the appendix before drawing conclusions.", "Esta seleção não tem avaliações negativas que mencionem os temas rastreados. Consulte o anexo antes de tirar conclusões."))
            } else {
                line(label("Prioridades según menciones negativas", "Priorities by negative mentions", "Prioridades por menções negativas"), 12f, ink, true, 12f)
                rankedTopics.forEachIndexed { index, (topic, matches) ->
                    if (y > bottom - 125f) startPage(continuationTitle)
                    val topicTitle = when (topic) {
                        "inicio de sesión" -> label("Inicio de sesión", "Login", "Login")
                        "rendimiento" -> label("Rendimiento", "Performance", "Desempenho")
                        "cierres inesperados" -> label("Cierres inesperados", "Unexpected crashes", "Fechamentos inesperados")
                        "notificaciones" -> label("Notificaciones", "Notifications", "Notificações")
                        "conectividad" -> label("Conectividad", "Connectivity", "Conectividade")
                        else -> label("Pagos", "Payments", "Pagamentos")
                    }
                    line("${index + 1}. $topicTitle · ${matches.size}/${reviews.size}", 11f, blue, true)
                    val evidence = matches.maxByOrNull { it.text.length }?.text.orEmpty()
                    if (evidence.isNotBlank()) paragraph("“${evidence.take(230)}${if (evidence.length > 230) "…" else ""}”", 9f, muted)
                    val action = when (topic) {
                        "inicio de sesión" -> label("Revisar el flujo de acceso y recuperación de contraseña; reproducir los errores con las versiones afectadas.", "Check login and password recovery; reproduce failures on affected versions.", "Revisar login e recuperação de senha; reproduzir falhas nas versões afetadas.")
                        "rendimiento" -> label("Medir tiempos de carga y bloqueos en las pantallas citadas; priorizar los dispositivos afectados.", "Measure loading times and freezes on cited screens; prioritize affected devices.", "Medir carregamentos e travamentos nas telas citadas; priorizar os dispositivos afetados.")
                        "cierres inesperados" -> label("Cruzar los reportes de cierres con registros de fallos y probar las rutas descritas en las reseñas.", "Compare crash reports with crash logs and test the flows described in reviews.", "Cruzar relatos de fechamento com logs de falhas e testar os fluxos descritos nas avaliações.")
                        "notificaciones" -> label("Verificar permisos, entrega y configuración de avisos en los casos citados.", "Check permissions, delivery and notification settings for the cited cases.", "Verificar permissões, entrega e configurações de notificações nos casos citados.")
                        "conectividad" -> label("Revisar errores de red, reintentos y experiencia sin conexión en las pantallas mencionadas.", "Review network errors, retries and offline behavior on the mentioned screens.", "Revisar erros de rede, novas tentativas e uso offline nas telas mencionadas.")
                        else -> label("Auditar el flujo de pago y cobros; contrastar los casos con registros de transacciones.", "Audit payment and charge flows; compare cases with transaction records.", "Auditar pagamento e cobranças; comparar casos com registros de transações.")
                    }
                    paragraph("${label("Acción sugerida:", "Suggested action:", "Ação sugerida:")} $action", 9f)
                    y += 15f
                }
            }
            if (y > bottom - 105f) startPage(continuationTitle)
            line(label("Cómo leer este informe", "How to read this report", "Como ler este relatório"), 12f, ink, true)
            paragraph(label("Cifras y gráficos: solo las reseñas que cumplen los filtros. Positivas: 4–5 estrellas; neutras: 3; negativas: 1–2. Los temas se detectan por palabras clave en el texto original; indican dónde investigar, no confirman una causa. La curva diaria refleja la media de las reseñas disponibles en cada fecha.", "Figures and charts: only reviews matching the filters. Positive: 4–5 stars; neutral: 3; negative: 1–2. Topics are detected by keywords in the original text; they suggest where to investigate, not a confirmed cause. The daily curve shows the average of available reviews for each date.", "Números e gráficos: apenas avaliações que atendem aos filtros. Positivas: 4–5 estrelas; neutras: 3; negativas: 1–2. Os temas são detectados por palavras-chave no texto original; indicam onde investigar, não uma causa confirmada. A curva diária mostra a média das avaliações disponíveis por data."), 9f, muted)

            paragraph(label("Google Play no informa el país del autor de cada reseña; el idioma es una estimación del texto.", "Google Play does not provide each reviewer's country; the language is estimated from the text.", "O Google Play não informa o país do autor de cada avaliação; o idioma é estimado a partir do texto."), 9f, muted)
            continuationTitle = label("Reseñas seleccionadas (continuación)", "Selected reviews (continued)", "Avaliações selecionadas (continuação)")
            startPage(label("Anexo · Reseñas seleccionadas", "Appendix · Selected reviews", "Anexo · Avaliações selecionadas"))
            paragraph(label("Se conserva el orden de la vista elegida. Los comentarios se reproducen en su idioma original.", "The chosen view order is preserved. Comments are shown in their original language.", "A ordem da visão escolhida é preservada. Os comentários aparecem no idioma original."), 9f, muted)
            y += 12f
            reviews.forEachIndexed { index, review ->
                if (y > bottom - 55f) startPage(continuationTitle)
                val date = review.date?.take(10) ?: label("Sin fecha", "No date", "Sem data")
                line("${index + 1}. ${review.rating}/5 ★   $date   ${review.language.uppercase(Locale.ROOT)}", 10f, blue, true, 5f)
                paragraph(review.text.ifBlank { label("Sin comentario", "No comment", "Sem comentário") })
                y += 12f
            }
            finishPage()
            return ByteArrayOutputStream().use { output -> pdf.writeTo(output); output.toByteArray() }
        } finally {
            pdf.close()
        }
    }
}
