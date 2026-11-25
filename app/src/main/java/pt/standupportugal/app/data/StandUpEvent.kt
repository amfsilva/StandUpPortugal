package pt.standupportugal.app.data

import kotlinx.serialization.Serializable
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@Serializable
data class EventsFeed(
    val updatedAt: String? = null,
    val events: List<StandUpEvent> = emptyList()
)

@Serializable
data class StandUpEvent(
    val id: String,
    val title: String,
    val dateTime: String, // ISO-8601 e.g. "2026-12-05T21:30:00+00:00"
    val venue: String,
    val district: String,
    val imageUrl: String = "",
    val ticketUrl: String,
    val source: String = "Web",
    val featured: Boolean = false
) {
    /**
     * Parses [dateTime] into a [ZonedDateTime] in Europe/Lisbon timezone.
     * Returns null if the string cannot be parsed.
     */
    fun parsedZonedDateTime(): ZonedDateTime? {
        val lisbonZone = ZoneId.of("Europe/Lisbon")
        return runCatching {
            OffsetDateTime.parse(dateTime).atZoneSameInstant(lisbonZone)
        }.recoverCatching {
            LocalDateTime.parse(dateTime).atZone(lisbonZone)
        }.recoverCatching {
            LocalDateTime.parse(dateTime.replace(" ", "T")).atZone(lisbonZone)
        }.getOrNull()
    }

    /**
     * Formats date/time in Portuguese, e.g. "Sáb, 5 Dez · 21:30".
     */
    fun formattedDateTimePt(): String {
        val zdt = parsedZonedDateTime() ?: return dateTime
        val localePt = Locale("pt", "PT")
        val dayFmt = DateTimeFormatter.ofPattern("EEE, d MMM · HH:mm", localePt)
        val raw = zdt.format(dayFmt)
        return raw.replaceFirstChar { if (it.isLowerCase()) it.titlecase(localePt) else it.toString() }
    }
}

object PortugalDistricts {
    val ALL = listOf(
        "Todos",
        "Lisboa",
        "Porto",
        "Braga",
        "Coimbra",
        "Faro",
        "Aveiro",
        "Setúbal",
        "Leiria",
        "Santarém",
        "Viseu",
        "Viana do Castelo",
        "Vila Real",
        "Bragança",
        "Castelo Branco",
        "Guarda",
        "Évora",
        "Beja",
        "Portalegre",
        "Madeira",
        "Açores"
    )
}
