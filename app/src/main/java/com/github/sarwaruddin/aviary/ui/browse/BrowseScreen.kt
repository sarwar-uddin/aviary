@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package com.github.sarwaruddin.aviary.ui.browse

import com.github.sarwaruddin.aviary.ui.about.AboutSheet
import androidx.compose.material.icons.outlined.Info
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.sarwaruddin.aviary.model.BdStatus
import com.github.sarwaruddin.aviary.model.BirdSummary
import com.github.sarwaruddin.aviary.model.Family
import com.github.sarwaruddin.aviary.model.ImageRef
import com.github.sarwaruddin.aviary.model.Iucn
import com.github.sarwaruddin.aviary.model.Season
import com.github.sarwaruddin.aviary.ui.common.BirdImage
import com.github.sarwaruddin.aviary.ui.common.GuideChip
import com.github.sarwaruddin.aviary.ui.common.GuidePillShape
import com.github.sarwaruddin.aviary.ui.common.GuideScreenTitle
import com.github.sarwaruddin.aviary.ui.common.PlateNumber
import com.github.sarwaruddin.aviary.ui.common.SciNameText
import com.github.sarwaruddin.aviary.ui.common.SeasonDot
import com.github.sarwaruddin.aviary.ui.common.SeasonTag
import com.github.sarwaruddin.aviary.ui.common.SectionHeader
import com.github.sarwaruddin.aviary.ui.common.StatusTag
import com.github.sarwaruddin.aviary.ui.common.Tag
import com.github.sarwaruddin.aviary.ui.common.plateClickable
import com.github.sarwaruddin.aviary.ui.common.seasonColor
import com.github.sarwaruddin.aviary.ui.theme.Field
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

private val ThumbShape = RoundedCornerShape(6.dp)
private val CardShape = RoundedCornerShape(10.dp)
private const val THUMB_PX = 300
private const val HERO_PX = 720
private val HeroDate: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMMM", Locale.ENGLISH)

/** Short season names for the trailing edge of a row. */
private fun Season.short(): String = when (this) {
    Season.RESIDENT -> "Resident"
    Season.WINTER -> "Winter"
    Season.SUMMER -> "Summer"
    Season.PASSAGE -> "Passage"
    Season.VAGRANT -> "Vagrant"
}

@Composable
fun BrowseScreen(onOpenBird: (String) -> Unit) {
    val vm: BrowseViewModel = viewModel()
    val f = Field.colors
    var showFamilies by remember { mutableStateOf(false) }

    // Collapsing title: scrolling the list up first folds the "Aviary" block away;
    // pulling down at the top of the list brings it back.
    var showAbout by remember { mutableStateOf(false) }
    var titleHeight by remember { mutableIntStateOf(0) }
    var titleOffset by remember { mutableFloatStateOf(0f) }   // 0 (open) .. -titleHeight (folded)
    val collapse = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (available.y >= 0f) return Offset.Zero
                val old = titleOffset
                titleOffset = (old + available.y).coerceIn(-titleHeight.toFloat(), 0f)
                return Offset(0f, titleOffset - old)
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (available.y <= 0f) return Offset.Zero
                val old = titleOffset
                titleOffset = (old + available.y).coerceIn(-titleHeight.toFloat(), 0f)
                return Offset(0f, titleOffset - old)
            }
        }
    }

    val filtered = vm.hasFilters || vm.resultsQuery.isNotBlank()
    val showHero = vm.query.isBlank() && vm.resultsQuery.isBlank() && !vm.hasFilters

    Column(
        Modifier
            .fillMaxSize()
            .background(f.paper)
            .nestedScroll(collapse),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .clipToBounds()
                .layout { measurable, constraints ->
                    val p = measurable.measure(constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity))
                    val off = titleOffset.roundToInt()
                    layout(p.width, (p.height + off).coerceAtLeast(0)) { p.placeRelative(0, off) }
                }
                .graphicsLayer {
                    alpha = if (titleHeight == 0) 1f else (1f + titleOffset / titleHeight).coerceIn(0f, 1f)
                },
        ) {
            GuideScreenTitle(
                title = "Aviary",
                subtitle = "Birds of Bangladesh · বাংলাদেশের পাখি",
                modifier = Modifier.onSizeChanged { titleHeight = it.height },
                action = {
                    IconButton(onClick = { showAbout = true }) {
                        Icon(Icons.Outlined.Info, contentDescription = "About Aviary and credits", tint = Field.colors.inkSoft)
                    }
                },
            )
        }
        if (showAbout) AboutSheet(onDismiss = { showAbout = false })

        SearchPill(query = vm.query, onQuery = vm::updateQuery)
        FilterRow(vm = vm, onPickFamily = { showFamilies = true })
        CountRow(
            total = vm.total,
            shown = vm.results.size,
            filtered = filtered,
            grid = vm.grid,
            onGrid = vm::showGrid,
        )
        HorizontalDivider(color = f.rule, thickness = 1.dp)

        Box(Modifier.weight(1f).fillMaxWidth()) {
            val results = vm.results
            val grouped = vm.resultsQuery.isBlank()
            val groups = remember(results, grouped) {
                if (grouped) results.groupBy { it.familyId } else null
            }
            val hero = if (showHero) vm.birdOfDay else null
            when {
                results.isEmpty() && vm.loading ->
                    CircularProgressIndicator(
                        color = f.moss, strokeWidth = 2.dp,
                        modifier = Modifier.align(Alignment.Center).size(28.dp),
                    )
                results.isEmpty() -> EmptyState(
                    error = vm.error,
                    query = vm.resultsQuery,
                    rareHidden = !vm.includeRare,
                    hasFilters = vm.hasFilters,
                    onShowRare = vm::toggleRare,
                    onClearFilters = vm::clearFilters,
                )
                vm.grid -> BirdGrid(vm, results, groups, hero, vm.birdOfDayPhoto, onOpenBird)
                else -> BirdList(vm, results, groups, hero, vm.birdOfDayPhoto, onOpenBird)
            }
        }
    }

    if (showFamilies) {
        FamilySheet(
            families = vm.families,
            selected = vm.familyId,
            onPick = { vm.pickFamily(it) },
            onDismiss = { showFamilies = false },
        )
    }
}

// ---------------------------------------------------------------- search, chips, count

@Composable
private fun SearchPill(query: String, onQuery: (String) -> Unit) {
    val f = Field.colors
    val focus = LocalFocusManager.current
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    BasicTextField(
        value = query,
        onValueChange = onQuery,
        singleLine = true,
        interactionSource = source,
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = f.ink, fontSize = 16.sp),
        cursorBrush = SolidColor(f.moss),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        decorationBox = { inner ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clip(GuidePillShape)
                    .background(f.card)
                    .border(1.dp, if (focused) f.moss else f.rule, GuidePillShape)
                    .padding(start = 16.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Search, contentDescription = null, tint = if (focused) f.moss else f.inkSoft,
                    modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (query.isEmpty()) {
                        Text(
                            "Search English, বাংলা or Latin",
                            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp),
                            color = f.inkSoft, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                    inner()
                }
                if (query.isNotEmpty()) {
                    IconButton(onClick = { onQuery("") }) {
                        Icon(Icons.Filled.Clear, contentDescription = "Clear search", tint = f.inkSoft,
                            modifier = Modifier.size(20.dp))
                    }
                } else {
                    Spacer(Modifier.width(12.dp))
                }
            }
        },
    )
}

@Composable
private fun FilterRow(vm: BrowseViewModel, onPickFamily: () -> Unit) {
    val familyLabel = vm.familyId?.let { vm.familiesById[it]?.commonName ?: it } ?: "Family"
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GuideChip(familyLabel, selected = vm.familyId != null, onClick = onPickFamily, arrow = true, maxLabelWidth = 170.dp)
        listOf(Season.RESIDENT, Season.WINTER, Season.SUMMER, Season.PASSAGE).forEach { s ->
            GuideChip(
                s.short(),
                selected = s in vm.seasons,
                onClick = { vm.toggleSeason(s) },
                dot = seasonColor(s),
            )
        }
        GuideChip("Threatened", selected = vm.threatenedOnly, onClick = vm::toggleThreatened, dot = Field.colors.rust)
        GuideChip("Show rare", selected = vm.includeRare, onClick = vm::toggleRare)
    }
}

@Composable
private fun CountRow(total: Int?, shown: Int, filtered: Boolean, grid: Boolean, onGrid: (Boolean) -> Unit) {
    val f = Field.colors
    Row(
        Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val count = when {
            total == null -> ""
            filtered -> "$shown of $total species"
            else -> "$total species"
        }
        Text(
            count.uppercase(),
            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.6.sp),
            color = f.inkSoft,
            modifier = Modifier.weight(1f),
        )
        ViewToggle(grid = grid, onGrid = onGrid)
    }
}

/** Small icon-text toggle: ≡ List | ▦ Plates. */
@Composable
private fun ViewToggle(grid: Boolean, onGrid: (Boolean) -> Unit) {
    val f = Field.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        listOf(false to "List", true to "Plates").forEach { (isGrid, label) ->
            val sel = grid == isGrid
            val c = if (sel) f.moss else f.inkSoft
            Row(
                Modifier
                    .clip(GuidePillShape)
                    .semantics { selected = sel }
                    .clickable(role = Role.Tab) { onGrid(isGrid) }
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ViewGlyph(grid = isGrid, color = c)
                Spacer(Modifier.width(5.dp))
                Text(
                    label,
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = if (sel) FontWeight.Bold else FontWeight.Medium,
                    ),
                    color = c,
                )
            }
        }
    }
}

/** Tiny drawn glyphs (icons-core has no grid icon): three bars, or four squares. */
@Composable
private fun ViewGlyph(grid: Boolean, color: Color) {
    Canvas(Modifier.size(12.dp)) {
        val w = size.width
        if (grid) {
            val gap = w * 0.16f
            val cell = (w - gap) / 2
            val r = CornerRadius(w * 0.08f)
            for (x in 0..1) for (y in 0..1) {
                drawRoundRect(color, Offset(x * (cell + gap), y * (cell + gap)), Size(cell, cell), r)
            }
        } else {
            val bar = w * 0.16f
            for (i in 0..2) {
                val y = w * (0.12f + 0.38f * i)
                drawRect(color, Offset(0f, y - bar / 2), Size(w, bar))
            }
        }
    }
}

// ---------------------------------------------------------------- bird of the day

@Composable
private fun BirdOfDayCard(b: BirdSummary, photo: ImageRef?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val f = Field.colors
    Column(modifier.fillMaxWidth()) {
        SectionHeader("Bird of the day", trailing = LocalDate.now().format(HeroDate))
        Spacer(Modifier.height(10.dp))
        Column(
            Modifier
                .fillMaxWidth()
                .plateClickable(onClick)
                .clip(CardShape)
                .background(f.card)
                .border(1.dp, f.rule, CardShape),
        ) {
            Box(Modifier.fillMaxWidth().aspectRatio(16f / 10f).background(f.rule.copy(alpha = 0.35f))) {
                BirdImage(ref = b.thumb, contentDescription = b.nameEn, modifier = Modifier.fillMaxSize(), maxPx = THUMB_PX)
                if (photo != null) {
                    BirdImage(ref = photo, contentDescription = null, modifier = Modifier.fillMaxSize(),
                        maxPx = HERO_PX, placeholder = false)
                }
            }
            HorizontalDivider(color = f.rule, thickness = 1.dp)
            Row(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 14.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        b.nameEn,
                        style = MaterialTheme.typography.headlineSmall,
                        color = f.ink, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    )
                    if (!b.nameBn.isNullOrBlank()) {
                        Text(
                            b.nameBn,
                            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 20.sp, lineHeight = 28.sp),
                            color = f.moss, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                    SciNameText(b.nameSci)
                }
                Spacer(Modifier.width(12.dp))
                Column(horizontalAlignment = Alignment.End) {
                    PlateNumber(b.sort, Modifier.padding(top = 4.dp, bottom = 8.dp))
                    b.season?.let { SeasonTag(it) }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- list

@Composable
private fun BirdList(
    vm: BrowseViewModel,
    results: List<BirdSummary>,
    groups: Map<String, List<BirdSummary>>?,
    hero: BirdSummary?,
    heroPhoto: ImageRef?,
    onOpenBird: (String) -> Unit,
) {
    LazyColumn(
        state = vm.listState,
        contentPadding = PaddingValues(bottom = 24.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        if (hero != null) {
            item(key = "botd", contentType = "hero") {
                BirdOfDayCard(
                    hero, heroPhoto, onClick = { onOpenBird(hero.id) },
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
                )
            }
        }
        if (groups != null) {
            groups.forEach { (famId, birds) ->
                stickyHeader(key = "fam:$famId", contentType = "header") {
                    FamilyHeader(vm.familiesById[famId], famId, birds.size, Modifier.padding(horizontal = 20.dp))
                }
                items(birds, key = { it.id }, contentType = { "bird" }) { b ->
                    BirdRow(b) { onOpenBird(b.id) }
                }
            }
        } else {
            items(results, key = { it.id }, contentType = { "bird" }) { b ->
                BirdRow(b) { onOpenBird(b.id) }
            }
        }
    }
}

/** Sticky family header: serif common name, Latin family in small caps on a hairline rule, count. */
@Composable
private fun FamilyHeader(family: Family?, familyId: String, count: Int, inset: Modifier) {
    val f = Field.colors
    Column(
        Modifier
            .fillMaxWidth()
            .background(f.paper)
            .then(inset)
            .padding(top = 18.dp, bottom = 6.dp),
    ) {
        Text(
            family?.commonName ?: familyId.replaceFirstChar { it.uppercase() },
            style = MaterialTheme.typography.titleLarge,
            color = f.ink,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        Row(Modifier.padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                (family?.name ?: familyId).uppercase(),
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Normal,
                    letterSpacing = 1.8.sp,
                ),
                color = f.moss,
                maxLines = 1,
            )
            Spacer(Modifier.width(10.dp))
            HorizontalDivider(Modifier.weight(1f), thickness = 1.dp, color = f.rule)
            Spacer(Modifier.width(10.dp))
            Text(
                count.toString(),
                style = MaterialTheme.typography.labelMedium,
                color = f.inkSoft,
            )
        }
    }
}

@Composable
private fun BirdRow(b: BirdSummary, onClick: () -> Unit) {
    val f = Field.colors
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(72.dp)
                    .clip(ThumbShape)
                    .background(f.rule.copy(alpha = 0.35f))
                    .border(1.dp, f.rule, ThumbShape),
            ) {
                BirdImage(ref = b.thumb, contentDescription = null, modifier = Modifier.fillMaxSize(), maxPx = THUMB_PX)
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    b.nameEn,
                    style = MaterialTheme.typography.titleMedium,
                    color = f.ink,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                if (!b.nameBn.isNullOrBlank()) BanglaName(b.nameBn, fontSize = 15)
                SciNameText(b.nameSci)
            }
            RowTrailing(b)
        }
        HorizontalDivider(
            color = f.rule.copy(alpha = 0.55f), thickness = 1.dp,
            modifier = Modifier.padding(start = 108.dp, end = 16.dp),
        )
    }
}

/** Season dot + label, then any notable Red List code or status underneath. */
@Composable
private fun RowTrailing(b: BirdSummary) {
    val f = Field.colors
    val iucn = b.iucn?.takeIf { it != Iucn.LC && it != Iucn.NE && it != Iucn.DD }
    if (b.season == null && iucn == null && b.status == BdStatus.REGULAR) return
    Column(Modifier.padding(start = 10.dp), horizontalAlignment = Alignment.End) {
        b.season?.let { s ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                SeasonDot(s, size = 7)
                Spacer(Modifier.width(5.dp))
                Text(s.short(), style = MaterialTheme.typography.labelSmall, color = f.inkSoft, maxLines = 1)
            }
        }
        if (iucn != null) {
            Tag(iucn.code, if (iucn.threatened) f.rust else f.ochre, Modifier.padding(top = 5.dp), filled = iucn.threatened)
        }
        if (b.status != BdStatus.REGULAR) StatusTag(b.status, Modifier.padding(top = 5.dp))
    }
}

@Composable
private fun BanglaName(name: String, fontSize: Int, color: Color = Field.colors.ink) {
    // System font: Bangla falls back to Noto (Sans/Serif) Bengali on device.
    Text(
        name,
        style = MaterialTheme.typography.bodyMedium.copy(fontSize = fontSize.sp, lineHeight = (fontSize + 7).sp),
        color = color,
        maxLines = 1, overflow = TextOverflow.Ellipsis,
    )
}

// ---------------------------------------------------------------- plates grid

@Composable
private fun BirdGrid(
    vm: BrowseViewModel,
    results: List<BirdSummary>,
    groups: Map<String, List<BirdSummary>>?,
    hero: BirdSummary?,
    heroPhoto: ImageRef?,
    onOpenBird: (String) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        state = vm.gridState,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        if (hero != null) {
            item(key = "botd", span = { GridItemSpan(maxLineSpan) }, contentType = "hero") {
                BirdOfDayCard(hero, heroPhoto, onClick = { onOpenBird(hero.id) }, modifier = Modifier.padding(top = 12.dp))
            }
        }
        if (groups != null) {
            groups.forEach { (famId, birds) ->
                item(key = "fam:$famId", span = { GridItemSpan(maxLineSpan) }, contentType = "header") {
                    FamilyHeader(vm.familiesById[famId], famId, birds.size, Modifier.padding(horizontal = 4.dp))
                }
                items(birds, key = { it.id }, contentType = { "plate" }) { b ->
                    BirdPlate(b) { onOpenBird(b.id) }
                }
            }
        } else {
            items(results, key = { it.id }, contentType = { "plate" }) { b ->
                BirdPlate(b) { onOpenBird(b.id) }
            }
        }
    }
}

/** Museum-label card: a mounted 4:5 photo, then plate number and names. */
@Composable
private fun BirdPlate(b: BirdSummary, onClick: () -> Unit) {
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
                if (b.season != null) SeasonDot(b.season, size = 7)
            }
            Text(
                b.nameEn,
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 15.sp, lineHeight = 20.sp),
                color = f.ink,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
            BanglaName(b.nameBn?.takeIf { it.isNotBlank() } ?: " ", fontSize = 14, color = f.moss)
            Text(
                b.nameSci,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Serif, fontStyle = FontStyle.Italic),
                color = f.inkSoft,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

// ---------------------------------------------------------------- empty + families

@Composable
private fun EmptyState(
    error: String?,
    query: String,
    rareHidden: Boolean,
    hasFilters: Boolean,
    onShowRare: () -> Unit,
    onClearFilters: () -> Unit,
) {
    val f = Field.colors
    // Scrollable so a pull-down still re-opens the collapsed title.
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 32.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("❦", color = f.ochre, fontFamily = FontFamily.Serif, fontSize = 22.sp)
        Spacer(Modifier.height(8.dp))
        Text(
            if (error != null) "The bird list could not be opened." else "No birds match.",
            style = MaterialTheme.typography.titleLarge,
            color = f.ink,
            textAlign = TextAlign.Center,
        )
        Text(
            when {
                error != null -> error
                query.isNotBlank() -> "Nothing found for “$query”. Try another spelling, a Bangla name, or part of the Latin name."
                else -> "Try fewer filters."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = f.inkSoft,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp),
        )
        if (error == null) {
            Spacer(Modifier.height(12.dp))
            if (rareHidden) {
                TextButton(onClick = onShowRare) { Text("Include rare and vagrant birds", color = f.moss) }
            }
            if (hasFilters) {
                TextButton(onClick = onClearFilters) { Text("Clear filters", color = f.moss) }
            }
        }
    }
}

@Composable
private fun FamilySheet(
    families: List<Family>,
    selected: String?,
    onPick: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    val f = Field.colors
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    val scope = rememberCoroutineScope()
    val pick: (String?) -> Unit = { id ->
        onPick(id)
        scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = f.card,
    ) {
        Text(
            "Families",
            style = MaterialTheme.typography.headlineSmall,
            color = f.ink,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp),
        )
        Text(
            "${families.size} families in the guide".uppercase(),
            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.6.sp),
            color = f.inkSoft,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 2.dp, bottom = 10.dp),
        )
        HorizontalDivider(color = f.rule, thickness = 1.dp)
        LazyColumn(contentPadding = PaddingValues(bottom = 32.dp)) {
            item(key = "all", contentType = "option") {
                FamilyOption("All families", null, selected == null) { pick(null) }
            }
            items(families, key = { it.id }, contentType = { "option" }) { fam ->
                FamilyOption(fam.commonName, fam.name, selected == fam.id) { pick(fam.id) }
            }
        }
    }
}

@Composable
private fun FamilyOption(title: String, latin: String?, selected: Boolean, onClick: () -> Unit) {
    val f = Field.colors
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (selected) f.fern.copy(alpha = 0.16f) else f.card)
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = if (selected) f.moss else f.ink)
            if (latin != null) {
                Text(
                    latin.uppercase(),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = FontFamily.Serif, fontWeight = FontWeight.Normal, letterSpacing = 1.6.sp,
                    ),
                    color = f.inkSoft,
                )
            }
        }
        if (selected) {
            Icon(Icons.Filled.Check, contentDescription = "Selected", tint = f.moss, modifier = Modifier.size(20.dp))
        }
    }
}
