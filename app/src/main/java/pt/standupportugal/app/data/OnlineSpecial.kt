package pt.standupportugal.app.data

import kotlinx.serialization.Serializable

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
    val year: String = "",
    val imageUrl: String = "",
    val watchUrl: String,
    val featured: Boolean = false,
    val priority: Int = 0
)

object OnlinePlatforms {
    val ALL = listOf(
        "Todos",
        "YouTube",
        "Netflix",
        "Max",
        "Prime Video",
        "RTP Play"
    )
}
