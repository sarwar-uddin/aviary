package com.github.sarwaruddin.aviary.ui.browse

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.sarwaruddin.aviary.data.AppGraph
import com.github.sarwaruddin.aviary.model.BirdFilter
import com.github.sarwaruddin.aviary.model.BirdSummary
import com.github.sarwaruddin.aviary.model.Family
import com.github.sarwaruddin.aviary.model.ImageRef
import com.github.sarwaruddin.aviary.model.Season
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * Browse state. Activity-scoped (via viewModel()), so the query, filters, view mode
 * and scroll positions survive tab switches and trips to the detail screen.
 */
@OptIn(FlowPreview::class)
class BrowseViewModel : ViewModel() {

    // What the user has typed / picked (UI reads these directly).
    var query by mutableStateOf("")
        private set
    var seasons by mutableStateOf<Set<Season>>(emptySet())
        private set
    var threatenedOnly by mutableStateOf(false)
        private set
    var includeRare by mutableStateOf(false)
        private set
    var familyId by mutableStateOf<String?>(null)
        private set
    var grid by mutableStateOf(false)
        private set

    // Loaded data.
    var families by mutableStateOf<List<Family>>(emptyList())
        private set
    var familiesById by mutableStateOf<Map<String, Family>>(emptyMap())
        private set
    var results by mutableStateOf<List<BirdSummary>>(emptyList())
        private set
    /** The query [results] were fetched with (lags [query] by the debounce). */
    var resultsQuery by mutableStateOf("")
        private set
    /** Total species in the dataset, rare ones included. */
    var total by mutableStateOf<Int?>(null)
        private set
    /** Bird of the day: picked from the date, among species with a season and a photo. */
    var birdOfDay by mutableStateOf<BirdSummary?>(null)
        private set
    /** A larger photo for the bird of the day (online); the thumbnail shows until it arrives. */
    var birdOfDayPhoto by mutableStateOf<ImageRef?>(null)
        private set
    var loading by mutableStateOf(true)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    // Kept here so scroll positions survive leaving the composition.
    val listState = LazyListState()
    val gridState = LazyGridState()

    private val queryFlow = MutableStateFlow("")
    private val filterFlow = MutableStateFlow(BirdFilter())
    private var loadedOnce = false

    val hasFilters: Boolean
        get() = seasons.isNotEmpty() || threatenedOnly || familyId != null

    init {
        viewModelScope.launch {
            val fams = runCatching { AppGraph.birds.families() }.getOrDefault(emptyList())
            families = fams.sortedBy { it.sort }
            familiesById = fams.associateBy { it.id }
            val all = runCatching { AppGraph.birds.birds(BirdFilter(includeRare = true)) }.getOrNull()
            total = all?.size
            val pick = all?.let { pickBirdOfDay(it, LocalDate.now()) }
            // The hero is inserted above an already-shown list; lazy lists keep the old first
            // item anchored, which would hide the hero. Pull back to the top if we're there.
            if (pick != null && listState.firstVisibleItemIndex <= 1) listState.requestScrollToItem(0)
            if (pick != null && gridState.firstVisibleItemIndex <= 1) gridState.requestScrollToItem(0)
            birdOfDay = pick
            if (pick != null) {
                birdOfDayPhoto = runCatching { AppGraph.birds.bird(pick.id)?.photos?.firstOrNull()?.large }.getOrNull()
            }
        }
        viewModelScope.launch {
            val debouncedQuery = queryFlow
                .debounce { if (it.isBlank()) 0L else 200L }
                .map { it.trim() }
                .distinctUntilChanged()
            combine(debouncedQuery, filterFlow) { q, f -> f.copy(query = q) }
                .distinctUntilChanged()
                .collectLatest { filter ->
                    loading = true
                    try {
                        val r = AppGraph.birds.birds(filter)
                        results = r
                        resultsQuery = filter.query
                        error = null
                        if (loadedOnce) {
                            // New result set: start again from the top on the next layout.
                            listState.requestScrollToItem(0)
                            gridState.requestScrollToItem(0)
                        }
                        loadedOnce = true
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        error = e.message ?: "Could not load birds"
                    } finally {
                        loading = false
                    }
                }
        }
    }

    fun updateQuery(q: String) {
        query = q
        queryFlow.value = q
    }

    fun toggleSeason(s: Season) {
        seasons = if (s in seasons) seasons - s else seasons + s
        pushFilter()
    }

    fun toggleThreatened() {
        threatenedOnly = !threatenedOnly
        pushFilter()
    }

    fun toggleRare() {
        includeRare = !includeRare
        pushFilter()
    }

    fun pickFamily(id: String?) {
        familyId = id
        pushFilter()
    }

    fun clearFilters() {
        seasons = emptySet()
        threatenedOnly = false
        familyId = null
        pushFilter()
    }

    fun showGrid(on: Boolean) {
        grid = on
    }

    private fun pushFilter() {
        filterFlow.value = BirdFilter(
            familyId = familyId,
            seasons = seasons,
            threatenedOnly = threatenedOnly,
            includeRare = includeRare,
        )
    }
}

/**
 * Deterministic "bird of the day": the same bird all day, a different one tomorrow.
 * Candidates are birds with a known season (not vagrants) and a picture, in guide order.
 */
internal fun pickBirdOfDay(all: List<BirdSummary>, date: LocalDate): BirdSummary? {
    val candidates = all
        .filter { b ->
            b.season != null && b.season != Season.VAGRANT &&
                b.thumb.let { it != null && (it.asset != null || it.url != null) }
        }
        .sortedBy { it.sort }
    if (candidates.isEmpty()) return null
    val index = Math.floorMod(date.year * 367 + date.dayOfYear, candidates.size)
    return candidates[index]
}
