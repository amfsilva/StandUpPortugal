package pt.standupportugal.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import pt.standupportugal.app.data.EventRepository
import pt.standupportugal.app.data.StandUpEvent
import pt.standupportugal.app.ui.StandUpViewModel
import java.time.ZoneId
import java.time.ZonedDateTime

class EventRepositoryTest {

    @Test
    fun mergeAndSortEvents_filtersPastEventsAndSortsAscending() {
        val now = ZonedDateTime.of(2026, 11, 25, 12, 0, 0, 0, ZoneId.of("Europe/Lisbon"))
        val past = StandUpEvent(
            id = "past",
            title = "Past Show",
            dateTime = "2026-11-24T21:00:00+00:00",
            venue = "Club",
            district = "Lisboa",
            ticketUrl = "https://example.com/past"
        )
        val later = StandUpEvent(
            id = "later",
            title = "Later Show",
            dateTime = "2026-12-10T21:00:00+00:00",
            venue = "Teatro",
            district = "Porto",
            ticketUrl = "https://example.com/later"
        )
        val sooner = StandUpEvent(
            id = "sooner",
            title = "Sooner Show",
            dateTime = "2026-11-28T21:30:00+00:00",
            venue = "Sala",
            district = "Lisboa",
            ticketUrl = "https://example.com/sooner"
        )

        val result = EventRepository.mergeAndSortEvents(
            primary = listOf(later, past, sooner),
            extra = emptyList(),
            now = now
        )

        assertEquals(2, result.size)
        assertEquals("sooner", result[0].id)
        assertEquals("later", result[1].id)
    }

    @Test
    fun parseGoogleSheetCsv_parsesFeaturedAndDistrict() {
        val csv = """
            title,dateTime,venue,district,imageUrl,ticketUrl,source,featured
            "Rir em Lisboa","2026-12-01T21:30:00+00:00","Cinema São Jorge","Lisboa","","https://bol.pt/1","Facebook",TRUE
        """.trimIndent()

        val events = EventRepository.parseGoogleSheetCsv(csv)
        assertEquals(1, events.size)
        assertEquals("Rir em Lisboa", events[0].title)
        assertEquals("Lisboa", events[0].district)
        assertTrue(events[0].featured)
    }

    @Test
    fun filterByDistrict_returnsOnlyMatchingDistrict() {
        val events = listOf(
            StandUpEvent("1", "A", "2026-12-01T21:00:00Z", "V1", "Lisboa", "", "https://a.pt"),
            StandUpEvent("2", "B", "2026-12-02T21:00:00Z", "V2", "Porto", "", "https://b.pt")
        )
        val porto = StandUpViewModel.filterByDistrict(events, "Porto")
        assertEquals(1, porto.size)
        assertEquals("Porto", porto[0].district)
    }
}
