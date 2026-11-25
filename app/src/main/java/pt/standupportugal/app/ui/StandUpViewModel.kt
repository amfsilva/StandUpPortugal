package pt.standupportugal.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pt.standupportugal.app.data.EventRepository
import pt.standupportugal.app.data.FeedOrigin
import pt.standupportugal.app.data.PortugalDistricts
import pt.standupportugal.app.data.StandUpEvent

data class StandUpUiState(
    val isLoading: Boolean = true,
    val selectedDistrict: String = "Todos",
    val searchQuery: String = "",
    val allEvents: List<StandUpEvent> = emptyList(),
    val filteredEvents: List<StandUpEvent> = emptyList(),
    val availableDistricts: List<String> = PortugalDistricts.ALL,
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
                repository.loadUpcomingEvents()
            }.onSuccess { result ->
                _uiState.update { current ->
                    val filtered = applyFilters(result.events, current.selectedDistrict, current.searchQuery)
                    current.copy(
                        isLoading = false,
                        allEvents = result.events,
                        filteredEvents = filtered,
                        feedOrigin = result.origin
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

    fun selectDistrict(district: String) {
        _uiState.update { current ->
            current.copy(
                selectedDistrict = district,
                filteredEvents = applyFilters(current.allEvents, district, current.searchQuery)
            )
        }
    }

    fun updateSearchQuery(query: String) {
        _uiState.update { current ->
            current.copy(
                searchQuery = query,
                filteredEvents = applyFilters(current.allEvents, current.selectedDistrict, query)
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
    }
}
