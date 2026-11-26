package pt.standupportugal.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pt.standupportugal.app.data.EventRepository
import pt.standupportugal.app.data.FeedOrigin
import pt.standupportugal.app.data.OnlinePlatforms
import pt.standupportugal.app.data.OnlineSpecial
import pt.standupportugal.app.data.PortugalDistricts
import pt.standupportugal.app.data.StandUpEvent

enum class BrowseMode {
    LIVE,
    ONLINE
}

data class StandUpUiState(
    val isLoading: Boolean = true,
    val browseMode: BrowseMode = BrowseMode.LIVE,
    val selectedDistrict: String = "Todos",
    val selectedPlatform: String = "Todos",
    val searchQuery: String = "",
    val allEvents: List<StandUpEvent> = emptyList(),
    val filteredEvents: List<StandUpEvent> = emptyList(),
    val allOnlineSpecials: List<OnlineSpecial> = emptyList(),
    val filteredOnlineSpecials: List<OnlineSpecial> = emptyList(),
    val availableDistricts: List<String> = PortugalDistricts.ALL,
    val availablePlatforms: List<String> = OnlinePlatforms.ALL,
    val feedOrigin: FeedOrigin = FeedOrigin.REMOTE,
    val errorMessage: String? = null
)

class StandUpViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = EventRepository(application.applicationContext)

    private val _uiState = MutableStateFlow(StandUpUiState())
    val uiState: StateFlow<StandUpUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            runCatching {
                val liveDeferred = async { repository.loadUpcomingEvents() }
                val onlineDeferred = async { repository.loadOnlineSpecials() }
                liveDeferred.await() to onlineDeferred.await()
            }.onSuccess { (liveResult, onlineSpecials) ->
                _uiState.update { current ->
                    current.copy(
                        isLoading = false,
                        allEvents = liveResult.events,
                        filteredEvents = applyFilters(
                            liveResult.events,
                            current.selectedDistrict,
                            current.searchQuery
                        ),
                        allOnlineSpecials = onlineSpecials,
                        filteredOnlineSpecials = applyOnlineFilters(
                            onlineSpecials,
                            current.selectedPlatform,
                            current.searchQuery
                        ),
                        feedOrigin = liveResult.origin
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = err.message ?: "Erro ao carregar espetáculos"
                    )
                }
            }
        }
    }

    fun setBrowseMode(mode: BrowseMode) {
        _uiState.update { current ->
            if (current.browseMode == mode) return@update current
            current.copy(
                browseMode = mode,
                searchQuery = "",
                filteredEvents = applyFilters(current.allEvents, current.selectedDistrict, ""),
                filteredOnlineSpecials = applyOnlineFilters(
                    current.allOnlineSpecials,
                    current.selectedPlatform,
                    ""
                )
            )
        }
    }

    fun selectDistrict(district: String) {
        _uiState.update { current ->
            current.copy(
                selectedDistrict = district,
                filteredEvents = applyFilters(current.allEvents, district, current.searchQuery)
            )
        }
    }

    fun selectPlatform(platform: String) {
        _uiState.update { current ->
            current.copy(
                selectedPlatform = platform,
                filteredOnlineSpecials = applyOnlineFilters(
                    current.allOnlineSpecials,
                    platform,
                    current.searchQuery
                )
            )
        }
    }

    fun updateSearchQuery(query: String) {
        _uiState.update { current ->
            current.copy(
                searchQuery = query,
                filteredEvents = applyFilters(current.allEvents, current.selectedDistrict, query),
                filteredOnlineSpecials = applyOnlineFilters(
                    current.allOnlineSpecials,
                    current.selectedPlatform,
                    query
                )
            )
        }
    }

    companion object {
        fun filterByDistrict(events: List<StandUpEvent>, district: String): List<StandUpEvent> {
            if (district == "Todos") return events
            return events.filter { it.district.equals(district, ignoreCase = true) }
        }

        fun applyFilters(
            events: List<StandUpEvent>,
            district: String,
            query: String
        ): List<StandUpEvent> {
            val byDistrict = filterByDistrict(events, district)
            val trimmed = query.trim()
            if (trimmed.isEmpty()) return byDistrict
            return byDistrict.filter { event ->
                event.title.contains(trimmed, ignoreCase = true) ||
                    event.venue.contains(trimmed, ignoreCase = true) ||
                    event.district.contains(trimmed, ignoreCase = true)
            }
        }

        fun applyOnlineFilters(
            specials: List<OnlineSpecial>,
            platform: String,
            query: String
        ): List<OnlineSpecial> {
            val byPlatform = if (platform == "Todos") {
                specials
            } else {
                specials.filter { it.platform.equals(platform, ignoreCase = true) }
            }
            val trimmed = query.trim()
            if (trimmed.isEmpty()) return byPlatform
            return byPlatform.filter { sp ->
                sp.title.contains(trimmed, ignoreCase = true) ||
                    sp.comedian.contains(trimmed, ignoreCase = true) ||
                    sp.platform.contains(trimmed, ignoreCase = true)
            }
        }
    }
}
