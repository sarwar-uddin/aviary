package com.github.sarwaruddin.aviary.ui.month

import androidx.compose.ui.graphics.luminance
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridItemSpanScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.sarwaruddin.aviary.data.AppGraph
import com.github.sarwaruddin.aviary.model.BirdFilter
import com.github.sarwaruddin.aviary.model.BirdSummary
import com.github.sarwaruddin.aviary.model.Season
import com.github.sarwaruddin.aviary.ui.common.BirdImage
import com.github.sarwaruddin.aviary.ui.common.GuideChip
import com.github.sarwaruddin.aviary.ui.common.IucnTag
import com.github.sarwaruddin.aviary.ui.common.OrnamentDivider
import com.github.sarwaruddin.aviary.ui.common.PlateNumber
import com.github.sarwaruddin.aviary.ui.common.ProportionBar
import com.github.sarwaruddin.aviary.ui.common.SeasonDot
import com.github.sarwaruddin.aviary.ui.common.SectionHeader
import com.github.sarwaruddin.aviary.ui.common.plateClickable
import com.github.sarwaruddin.aviary.ui.common.seasonColor
import com.github.sarwaruddin.aviary.ui.theme.Field
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDate

private val ThumbShape = RoundedCornerShape(6.dp)
private val CardShape = RoundedCornerShape(10.dp)
private const val THUMB_PX = 300
private val Gutter = 16.dp

private val MonthsEn = listOf(
    "January", "February", "March", "April", "May", "June",
    "July", "August", "September", "October", "November", "December",
)

/** Gregorian month names as written in Bangla (not the Bengali calendar months). */
private val MonthsBn = listOf(
    "জানুয়ারি", "ফেব্রুয়ারি", "মার্চ", "এপ্রিল", "মে", "জুন",
    "জুলাই", "আগস্ট", "সেপ্টেম্বর", "অক্টোবর", "নভেম্বর", "ডিসেম্বর",
)

/** Section order: winter visitors first, residents last. */
private val SectionOrder = listOf(Season.WINTER, Season.SUMMER, Season.PASSAGE, Season.RESIDENT)

/** Order of the at-a-glance bar and its legend. */
private val BarOrder = listOf(Season.RESIDENT, Season.WINTER, Season.SUMMER, Season.PASSAGE, Season.VAGRANT)

class MonthViewModel : ViewModel() {
    val currentMonth: Int = LocalDate.now().monthValue

    var month by mutableIntStateOf(currentMonth)
        private set
    /** Birds expected in [month]; null until the first load finishes. */
    var birds by mutableStateOf<List<BirdSummary>?>(null)
        private set
    var loading by mutableStateOf(true)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    /** How many species have a curated season, and how many species there are in all. */
    var curated by mutableStateOf<Int?>(null)
        private set
    var total by mutableStateOf<Int?>(null)
        private set

    private var job: Job? = null

    init {
        load()
        viewModelScope.launch {
            runCatching { AppGraph.birds.birds(BirdFilter(includeRare = true)) }.getOrNull()?.let { all ->
                total = all.size
                curated = all.count { it.season != null }
            }
        }
    }

    fun select(m: Int) {
        if (m == month) return
        month = m
        load()
    }

    private fun load() {
        job?.cancel()
        val m = month
        job = viewModelScope.launch {
            loading = true
            try {
                birds = AppGraph.birds.aroundInMonth(m)
                error = null
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

@Composable
fun MonthScreen(onOpenBird: (String) -> Unit) {
    val vm: MonthViewModel = viewModel()
    val f = Field.colors
    val month = vm.month
    val birds = vm.birds

    val sections = remember(birds) {
        val bySeason = (birds ?: emptyList()).filter { it.season != null }.groupBy { it.season!! }
        SectionOrder.mapNotNull { s ->
            bySeason[s]?.takeIf { it.isNotEmpty() }?.let { s to it }
        }
    }
    val counts: List<Pair<Season, Int>> = remember(birds) {
        val bySeason = (birds ?: emptyList()).groupingBy { it.season }.eachCount()
        BarOrder.map { it to (bySeason[it] ?: 0) }
    }

    Box(Modifier.fillMaxSize().background(f.paper)) {
        MonthContent(
            month = month,
            current = vm.currentMonth,
            count = birds?.size,
            counts = counts,
            sections = sections,
            loading = birds == null && vm.loading,
            error = vm.error,
            curated = vm.curated,
            total = vm.total,
            onSelect = vm::select,
            onOpenBird = onOpenBird,
        )
    }
}

// ---------------------------------------------------------------- hero

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MonthHero(month: Int, current: Int, count: Int?, counts: List<Pair<Season, Int>>) {
    val f = Field.colors
    Column(Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 4.dp)) {
        Text(
            (if (month == current) "What's around now" else "What's around in").uppercase(),
            style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 2.sp),
            color = f.moss,
        )
        Text(
            MonthsEn[month - 1],
            style = MaterialTheme.typography.displaySmall.copy(fontSize = 46.sp, lineHeight = 52.sp),
            color = f.ink,
        )
        Text(
            MonthsBn[month - 1],
            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 22.sp, lineHeight = 30.sp),
            color = f.moss,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            when (count) {
                null -> " "
                1 -> "1 bird to look for"
                else -> "$count birds to look for"
            },
            style = MaterialTheme.typography.titleMedium.copy(fontStyle = FontStyle.Italic),
            color = f.inkSoft,
        )
        val parts = counts.filter { it.second > 0 }
        if (parts.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            ProportionBar(
                parts.map { seasonColor(it.first) to it.second },
                Modifier.clip(RoundedCornerShape(3.dp)),
                height = 8,
            )
            Spacer(Modifier.height(8.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                parts.forEach { (s, n) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SeasonDot(s, size = 7)
                        Spacer(Modifier.width(5.dp))
                        Text(
                            "${s.legend()} $n",
                            style = MaterialTheme.typography.labelSmall,
                            color = f.inkSoft,
                        )
                    }
                }
            }
        }
    }
}

private fun Season.legend(): String = when (this) {
    Season.RESIDENT -> "Resident"
    Season.WINTER -> "Winter"
    Season.SUMMER -> "Summer"
    Season.PASSAGE -> "Passage"
    Season.VAGRANT -> "Vagrant"
}

/** Month pills; the selected month is scrolled to the middle of the row. */
@Composable
private fun MonthRow(selected: Int, current: Int, onSelect: (Int) -> Unit) {
    val state = rememberLazyListState(initialFirstVisibleItemIndex = (selected - 3).coerceAtLeast(0))
    var first by remember { mutableStateOf(true) }
    LaunchedEffect(selected) {
        snapshotFlow { state.layoutInfo.visibleItemsInfo.isNotEmpty() }.first { it }
        centre(state, selected - 1, animate = !first)
        first = false
    }
    LazyRow(
        state = state,
        contentPadding = PaddingValues(horizontal = Gutter, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.bleed(Gutter),
    ) {
        items((1..12).toList(), key = { it }, contentType = { "month" }) { m ->
            GuideChip(
                label = MonthsEn[m - 1].take(3),
                selected = m == selected,
                onClick = { onSelect(m) },
                // A small moss dot marks "now", so the current month is always findable.
                dot = if (m == current) Field.colors.moss else null,
            )
        }
    }
}

private suspend fun centre(state: LazyListState, index: Int, animate: Boolean) {
    var item = state.layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }
    if (item == null) {
        state.scrollToItem(index)
        item = withTimeoutOrNull(500) {
            snapshotFlow { state.layoutInfo.visibleItemsInfo.firstOrNull { it.index == index } }.first { it != null }
        } ?: return
    }
    val info = state.layoutInfo
    val mid = (info.viewportStartOffset + info.viewportEndOffset) / 2
    val delta = (item.offset + item.size / 2 - mid).toFloat()
    if (animate) state.animateScrollBy(delta) else state.scrollBy(delta)
}

/** Lets a full-span item run edge to edge, past the grid's side padding. */
private fun Modifier.bleed(h: Dp): Modifier = layout { measurable, constraints ->
    val extra = (h * 2).roundToPx()
    val w = constraints.maxWidth + extra
    val p = measurable.measure(constraints.copy(minWidth = w, maxWidth = w))
    layout(constraints.maxWidth, p.height) { p.placeRelative(-extra / 2, 0) }
}

// ---------------------------------------------------------------- content

private fun phaseNote(s: Season, m: Int): String = when (s) {
    Season.WINTER -> when (m) {
        10 -> "Arriving from the north"
        3 -> "The last of them, before they leave"
        else -> "Here for the winter"
    }
    Season.SUMMER -> when (m) {
        3, 4 -> "Arriving for the summer"
        9 -> "Leaving soon"
        else -> "Here for the summer"
    }
    Season.PASSAGE -> if (m in 3..5) "Spring passage, heading north" else "Autumn passage, heading south"
    Season.RESIDENT -> "Here all year round"
    Season.VAGRANT -> ""
}

private fun sectionTitle(s: Season): String = when (s) {
    Season.WINTER -> "Winter visitors"
    Season.SUMMER -> "Summer visitors"
    Season.PASSAGE -> "Passage migrants"
    Season.RESIDENT -> "Residents"
    Season.VAGRANT -> "Vagrants"
}

@Composable
private fun MonthContent(
    month: Int,
    current: Int,
    count: Int?,
    counts: List<Pair<Season, Int>>,
    sections: List<Pair<Season, List<BirdSummary>>>,
    loading: Boolean,
    error: String?,
    curated: Int?,
    total: Int?,
    onSelect: (Int) -> Unit,
    onOpenBird: (String) -> Unit,
) {
    val f = Field.colors
    val fullSpan: (LazyGridItemSpanScope.() -> GridItemSpan) = { GridItemSpan(maxLineSpan) }

    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        contentPadding = PaddingValues(start = Gutter, end = Gutter, top = 0.dp, bottom = 28.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(key = "hero", span = fullSpan, contentType = "hero") {
            MonthHero(month, current, count, counts)
        }
        item(key = "months", span = fullSpan, contentType = "months") {
            Column {
                MonthRow(selected = month, current = current, onSelect = onSelect)
                HorizontalDivider(Modifier.bleed(Gutter), color = f.rule, thickness = 1.dp)
            }
        }

        if (loading) {
            item(key = "loading", span = fullSpan, contentType = "note") {
                Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = f.moss, strokeWidth = 2.dp, modifier = Modifier.size(28.dp))
                }
            }
        } else if (error != null || sections.isEmpty()) {
            item(key = "empty", span = fullSpan, contentType = "note") {
                Text(
                    if (error != null) "Could not open the bird list: $error"
                    else "No birds have seasons filled in for ${MonthsEn[month - 1]} yet.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = f.inkSoft,
                    modifier = Modifier.padding(vertical = 24.dp),
                )
            }
        }

        sections.forEach { (season, list) ->
            if (season == Season.RESIDENT) {
                item(key = "h:${season.name}", span = fullSpan, contentType = "section") {
                    Column(Modifier.padding(top = 18.dp)) {
                        SectionHeader(sectionTitle(season), trailing = list.size.toString())
                        Text(
                            season.labelBn + "  ·  " + phaseNote(season, month),
                            style = MaterialTheme.typography.bodyMedium,
                            color = f.inkSoft,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
                items(list, key = { "r:" + it.id }, contentType = { "cell" }) { b ->
                    ResidentCell(b) { onOpenBird(b.id) }
                }
            } else {
                item(key = "c:${season.name}", span = fullSpan, contentType = "carousel") {
                    SeasonCarousel(season, month, list, onOpenBird)
                }
            }
        }

        if (curated != null && !loading) {
            item(key = "coverage", span = fullSpan, contentType = "note") {
                CoverageNote(curated, total)
            }
        }
    }
}

/**
 * A migrant season: a band tinted with the season's colour on paper, a section header,
 * then a horizontal run of plate cards.
 */
@Composable
private fun SeasonCarousel(season: Season, month: Int, list: List<BirdSummary>, onOpenBird: (String) -> Unit) {
    val f = Field.colors
    val accent = seasonColor(season)
    // Blue over yellowish paper turns grey, so winter gets its own cool "frost" band in light mode.
    val light = f.paper.luminance() > 0.5f
    val band = when {
        season == Season.WINTER && light -> androidx.compose.ui.graphics.Color(0xFFE2ECF0)
        else -> accent.copy(alpha = 0.12f).compositeOver(f.paper)
    }
    Column(
        Modifier
            .padding(top = 14.dp)
            .bleed(Gutter)
            .background(band)
            .padding(top = 16.dp, bottom = 18.dp),
    ) {
        Column(Modifier.padding(horizontal = Gutter)) {
            SectionHeader(sectionTitle(season), trailing = list.size.toString())
            Text(
                season.labelBn + "  ·  " + phaseNote(season, month),
                style = MaterialTheme.typography.bodyMedium,
                color = accent,
                maxLines = 2, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
            )
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = Gutter),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(list, key = { it.id }, contentType = { "plate" }) { b ->
                CarouselPlate(b, accent) { onOpenBird(b.id) }
            }
        }
    }
}

@Composable
private fun CarouselPlate(b: BirdSummary, accent: Color, onClick: () -> Unit) {
    val f = Field.colors
    Column(
        Modifier
            .width(164.dp)
            .plateClickable(onClick)
            .clip(CardShape)
            .background(f.card)
            .border(1.dp, f.rule, CardShape),
    ) {
        Box(
            Modifier
                .padding(6.dp)
                .fillMaxWidth()
                .aspectRatio(4f / 5f)
                .clip(ThumbShape)
                .background(f.rule.copy(alpha = 0.35f)),
        ) {
            BirdImage(ref = b.thumb, contentDescription = b.nameEn, modifier = Modifier.fillMaxSize(), maxPx = THUMB_PX)
        }
        Column(Modifier.padding(start = 10.dp, end = 10.dp, top = 2.dp, bottom = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PlateNumber(b.sort, Modifier.weight(1f))
                Box(Modifier.size(width = 14.dp, height = 2.dp).background(accent))
            }
            Text(
                b.nameEn,
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 15.sp, lineHeight = 20.sp),
                color = f.ink,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
            Text(
                b.nameBn?.takeIf { it.isNotBlank() } ?: " ",
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 21.sp),
                color = f.moss,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Text(
                b.nameSci,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Serif, fontStyle = FontStyle.Italic),
                color = f.inkSoft,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            // Keep every card the same height; the tag shows only for notable categories.
            Box(Modifier.height(22.dp).padding(top = 4.dp)) {
                b.iucn?.let { IucnTag(it) }
            }
        }
    }
}

@Composable
private fun ResidentCell(b: BirdSummary, onClick: () -> Unit) {
    val f = Field.colors
    Column(
        Modifier
            .fillMaxWidth()
            .plateClickable(onClick)
            .clip(CardShape)
            .background(f.card)
            .border(1.dp, f.rule, CardShape),
    ) {
        Box(
            Modifier
                .padding(4.dp)
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(ThumbShape)
                .background(f.rule.copy(alpha = 0.35f)),
        ) {
            BirdImage(ref = b.thumb, contentDescription = b.nameEn, modifier = Modifier.fillMaxSize(), maxPx = THUMB_PX)
        }
        Column(Modifier.padding(start = 7.dp, end = 7.dp, top = 2.dp, bottom = 8.dp)) {
            Text(
                b.nameEn,
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 13.sp, lineHeight = 17.sp),
                color = f.ink,
                minLines = 2, maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
            Text(
                b.nameBn?.takeIf { it.isNotBlank() } ?: " ",
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, lineHeight = 19.sp),
                color = f.moss,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun CoverageNote(curated: Int, total: Int?) {
    val f = Field.colors
    Column(
        Modifier.fillMaxWidth().padding(top = 28.dp, start = 12.dp, end = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        OrnamentDivider()
        Text(
            "A NOTE ON COVERAGE",
            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 2.sp),
            color = f.moss,
            modifier = Modifier.padding(top = 4.dp, bottom = 6.dp),
        )
        Text(
            buildString {
                append("Seasons are filled in for $curated species so far, mostly the common ones")
                if (total != null) append(", out of $total recorded in Bangladesh")
                append(". Many other birds visit too; the Birds tab lists them all.")
            },
            style = MaterialTheme.typography.bodyMedium.copy(
                fontFamily = FontFamily.Serif, fontStyle = FontStyle.Italic, lineHeight = 21.sp,
            ),
            color = f.inkSoft,
            textAlign = TextAlign.Center,
        )
    }
}
