package pt.standupportugal.app.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import pt.standupportugal.app.BuildConfig
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.time.ZonedDateTime

enum class FeedOrigin {
    REMOTE,
    LOCAL_FALLBACK
}

data class EventLoadResult(
    val events: List<StandUpEvent>,
    val origin: FeedOrigin,
    val updatedAt: String? = null
)

class EventRepository(
    private val context: Context,
    private val feedUrl: String = BuildConfig.EVENTS_FEED_URL,
    private val onlineFeedUrl: String = BuildConfig.ONLINE_FEED_URL,
    private val googleSheetCsvUrl: String = BuildConfig.GOOGLE_SHEET_CSV_URL
) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    suspend fun loadUpcomingEvents(now: ZonedDateTime = ZonedDateTime.now()): EventLoadResult =
        withContext(Dispatchers.IO) {
            val remoteFeed = fetchRemoteJsonFeed()
            val sheetEvents = fetchGoogleSheetCsvEvents()

            val (baseEvents, origin, updatedAt) = if (remoteFeed != null && remoteFeed.events.isNotEmpty()) {
                Triple(remoteFeed.events, FeedOrigin.REMOTE, remoteFeed.updatedAt)
            } else if (sheetEvents.isNotEmpty()) {
                Triple(emptyList(), FeedOrigin.REMOTE, null)
            } else {
                val localFeed = loadBundledSampleFeed()
                Triple(localFeed.events, FeedOrigin.LOCAL_FALLBACK, localFeed.updatedAt)
            }

            val merged = mergeAndSortEvents(
                primary = baseEvents,
                extra = sheetEvents,
                now = now
            )
            EventLoadResult(events = merged, origin = origin, updatedAt = updatedAt)
        }

    suspend fun loadOnlineSpecials(): List<OnlineSpecial> = withContext(Dispatchers.IO) {
        val remote = fetchRemoteOnlineFeed()
        val list = if (remote != null && remote.specials.isNotEmpty()) {
            remote.specials
        } else {
            loadBundledOnlineFeed().specials
        }
        sortOnlineSpecials(list)
    }

    private fun fetchRemoteOnlineFeed(): OnlineSpecialsFeed? {
        if (onlineFeedUrl.isBlank() || onlineFeedUrl.contains("SEU-UTILIZADOR")) return null
        return runCatching {
            val raw = httpGet(onlineFeedUrl)
            json.decodeFromString<OnlineSpecialsFeed>(raw)
        }.getOrNull()
    }

    private fun loadBundledOnlineFeed(): OnlineSpecialsFeed {
        return runCatching {
            context.assets.open("sample_online_specials.json").bufferedReader().use { reader ->
                json.decodeFromString<OnlineSpecialsFeed>(reader.readText())
            }
        }.getOrDefault(OnlineSpecialsFeed())
    }

    private fun fetchRemoteJsonFeed(): EventsFeed? {
        if (feedUrl.isBlank() || feedUrl.contains("SEU-UTILIZADOR")) return null
        return runCatching {
            val raw = httpGet(feedUrl)
            json.decodeFromString<EventsFeed>(raw)
        }.getOrNull()
    }

    private fun fetchGoogleSheetCsvEvents(): List<StandUpEvent> {
        if (googleSheetCsvUrl.isBlank()) return emptyList()
        return runCatching {
            val csv = httpGet(googleSheetCsvUrl)
            parseGoogleSheetCsv(csv)
        }.getOrDefault(emptyList())
    }

    private fun loadBundledSampleFeed(): EventsFeed {
        return runCatching {
            context.assets.open("sample_events.json").bufferedReader().use { reader ->
                json.decodeFromString<EventsFeed>(reader.readText())
            }
        }.getOrDefault(EventsFeed())
    }

    private fun httpGet(urlString: String): String {
        val connection = (URL(urlString).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10_000
            readTimeout = 10_000
            setRequestProperty("Accept", "application/json, text/csv")
        }
        return try {
            val code = connection.responseCode
            if (code !in 200..299) {
                error("HTTP $code from $urlString")
            }
            BufferedReader(InputStreamReader(connection.inputStream, Charsets.UTF_8)).use {
                it.readText()
            }
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        fun sortOnlineSpecials(list: List<OnlineSpecial>): List<OnlineSpecial> {
            val featured = list.filter { it.featured }.sortedWith(
                compareByDescending<OnlineSpecial> { it.priority }
                    .thenByDescending { it.effectiveSortDate() }
                    .thenBy { it.title }
            )
            val regular = list.filter { !it.featured }.sortedWith(
                compareBy<OnlineSpecial> { it.languageGroupRank() }
                    .thenByDescending { it.effectiveSortDate() }
                    .thenByDescending { it.priority }
                    .thenBy { it.title }
            )
            return featured + regular
        }

        /**
         * Merges automatic feed events with manual/promoted Google Sheet events,
         * filters out past events (older than 2 hours ago), deduplicates by ticketUrl,
         * and sorts ascending by date/time (nearest shows first).
         */
        fun mergeAndSortEvents(
            primary: List<StandUpEvent>,
            extra: List<StandUpEvent>,
            now: ZonedDateTime = ZonedDateTime.now()
        ): List<StandUpEvent> {
            val cutoff = now.minusHours(2)
            val byUrl = LinkedHashMap<String, StandUpEvent>()

            // Extra (Google Sheet) events overwrite matching ticketUrl so manual edits/promotions win
            for (event in primary) {
                byUrl[event.ticketUrl.trim()] = event
            }
            for (event in extra) {
                byUrl[event.ticketUrl.trim()] = event
            }

            return byUrl.values
                .mapNotNull { event ->
                    val parsed = event.parsedZonedDateTime() ?: return@mapNotNull null
                    if (parsed.isBefore(cutoff)) return@mapNotNull null
                    event to parsed
                }
                .sortedWith(
                    compareBy<Pair<StandUpEvent, ZonedDateTime>> { it.second }
                        .thenByDescending { it.first.featured }
                )
                .map { it.first }
        }

        /**
         * Parses a published Google Sheets CSV with header:
         * title,dateTime,venue,district,imageUrl,ticketUrl,source,featured
         */
        fun parseGoogleSheetCsv(csvText: String): List<StandUpEvent> {
            val lines = csvText.lines().filter { it.isNotBlank() }
            if (lines.size <= 1) return emptyList()

            return lines.drop(1).mapIndexedNotNull { idx, line ->
                val cols = splitCsvLine(line)
                if (cols.size < 6) return@mapIndexedNotNull null
                val title = cols[0].trim()
                val dateTime = cols[1].trim()
                val venue = cols[2].trim()
                val district = cols[3].trim()
                val imageUrl = cols[4].trim()
                val ticketUrl = cols[5].trim()
                val source = cols.getOrNull(6)?.trim().orEmpty().ifBlank { "Curadoria" }
                val featured = cols.getOrNull(7)?.trim()?.lowercase() in setOf("true", "1", "sim", "yes")

                if (title.isBlank() || dateTime.isBlank() || ticketUrl.isBlank()) {
                    return@mapIndexedNotNull null
                }

                StandUpEvent(
                    id = "sheet-$idx-${ticketUrl.hashCode()}",
                    title = title,
                    dateTime = dateTime,
                    venue = venue,
                    district = district.ifBlank { "Portugal" },
                    imageUrl = imageUrl,
                    ticketUrl = ticketUrl,
                    source = source,
                    featured = featured
                )
            }
        }

        private fun splitCsvLine(line: String): List<String> {
            val result = mutableListOf<String>()
            val current = StringBuilder()
            var inQuotes = false
            var i = 0
            while (i < line.length) {
                val ch = line[i]
                when {
                    ch == '"' -> {
                        if (inQuotes && i + 1 < line.length && line[i + 1] == '"') {
                            current.append('"')
                            i++
                        } else {
                            inQuotes = !inQuotes
                        }
                    }
                    ch == ',' && !inQuotes -> {
                        result.add(current.toString())
                        current.clear()
                    }
                    else -> current.append(ch)
                }
                i++
            }
            result.add(current.toString())
            return result
        }
    }
}
