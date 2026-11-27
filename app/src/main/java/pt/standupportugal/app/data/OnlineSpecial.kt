package pt.standupportugal.app.data

import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Serializable
data class OnlineSpecialsFeed(
    val updatedAt: String? = null,
    val specials: List<OnlineSpecial> = emptyList()
)

@Serializable
data class OnlineSpecial(
    val id: String,
    val title: String,
    val comedian: String = "",
    val platform: String,
    val language: String = "PT",
    val publishedDate: String = "", // ISO YYYY-MM-DD
    val year: String = "",
    val imageUrl: String = "",
    val watchUrl: String,
    val featured: Boolean = false,
    val priority: Int = 0
) {
    fun effectiveSortDate(): String {
        if (publishedDate.isNotBlank()) return publishedDate
        if (year.isNotBlank()) return "${year.trim()}-01-01"
        return ""
    }

    /** 0 for Portugal (PT / PT-PT), 1 for Brazilian Portuguese (PT-BR / BR). */
    fun languageGroupRank(): Int {
        val upper = language.trim().uppercase()
        return if (upper.contains("BR")) 1 else 0
    }

    fun formattedPublishedDatePt(): String {
        val raw = publishedDate.trim()
        if (raw.isBlank()) return year.trim()
        return runCatching {
            val date = LocalDate.parse(raw)
            val localePt = Locale("pt", "PT")
            date.format(DateTimeFormatter.ofPattern("d MMM yyyy", localePt))
        }.getOrDefault(raw)
    }
}

object OnlinePlatforms {
    val ALL = listOf(
        "Todos",
        "YouTube",
        "Netflix",
        "HBO",
        "Prime Video",
        "RTP Play"
    )
}
