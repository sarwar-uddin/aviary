package com.github.sarwaruddin.aviary.ui.log

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.github.sarwaruddin.aviary.data.AppGraph
import com.github.sarwaruddin.aviary.model.BirdFilter
import com.github.sarwaruddin.aviary.model.BirdSummary
import com.github.sarwaruddin.aviary.model.LifeListEntry
import com.github.sarwaruddin.aviary.model.Sighting
import com.github.sarwaruddin.aviary.ui.common.GuideScreenTitle
import com.github.sarwaruddin.aviary.ui.common.GuideSegmented
import com.github.sarwaruddin.aviary.ui.common.PlateNumber
import com.github.sarwaruddin.aviary.ui.common.SectionHeader
import com.github.sarwaruddin.aviary.ui.theme.Field
import androidx.compose.foundation.border
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

private enum class LogTab(val label: String) { LIFE("Life list"), SIGHTINGS("Sightings") }

/** Sealed rows for the journal so the LazyColumn can key day headers and entries apart. */
private sealed interface JournalRow {
    data class Day(val date: LocalDate) : JournalRow
    data class Entry(val s: Sighting) : JournalRow
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogScreen(onOpenBird: (String) -> Unit, onLogSighting: () -> Unit) {
    val f = Field.colors
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val is24 = remember { android.text.format.DateFormat.is24HourFormat(context) }

    val lifeListFlow = remember { AppGraph.sightings.lifeList() }
    val sightingsFlow = remember { AppGraph.sightings.sightings() }
    val lifeList by lifeListFlow.collectAsState(initial = null)
    val sightings by sightingsFlow.collectAsState(initial = null)

    var tab by rememberSaveable { mutableIntStateOf(0) }
    var editing by remember { mutableStateOf<Sighting?>(null) }
    var deleting by remember { mutableStateOf<Sighting?>(null) }
    var quickLog by remember { mutableStateOf<String?>(null) }   // species id for the empty-state shortcut

    val life = lifeList
    val all = sightings
    // Every species with a sighting is on the life list, so it doubles as the name lookup.
    val birdsById: Map<String, BirdSummary> = remember(life) { life?.associate { it.bird.id to it.bird } ?: emptyMap() }
    val thisMonth = remember(all) {
        val ym = YearMonth.now()
        all?.count { YearMonth.from(it.timeMillis.toLocalDate()) == ym } ?: 0
    }
    val journal: List<JournalRow> = remember(all) {
        buildList {
            var day: LocalDate? = null
            all.orEmpty().sortedByDescending { it.timeMillis }.forEach { s ->
                val d = s.timeMillis.toLocalDate()
                if (d != day) { add(JournalRow.Day(d)); day = d }
                add(JournalRow.Entry(s))
            }
        }
    }

    Box(Modifier.fillMaxSize().background(f.paper)) {
        LazyColumn(
            Modifier.fillMaxSize(),
            // Bottom: room for the extended FAB (56dp + 16dp margin) plus breathing space.
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 0.dp, bottom = 112.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(key = "header", contentType = "header") {
                LogHeader(
                    species = life?.size ?: 0,
                    total = all?.size ?: 0,
                    thisMonth = thisMonth,
                )
            }

            when {
                life == null || all == null -> item(key = "loading", contentType = "loading") {
                    Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = f.moss)
                    }
                }

                all.isEmpty() -> item(key = "empty", contentType = "empty") {
                    EmptyLog(
                        onLogSighting = onLogSighting,
                        onLogDoyel = {
                            scope.launch {
                                val id = runCatching {
                                    AppGraph.birds.birds(BirdFilter(query = "Copsychus saularis", includeRare = true))
                                        .firstOrNull()?.id
                                }.getOrNull()
                                if (id != null) quickLog = id else onLogSighting()
                            }
                        },
                    )
                }

                else -> {
                    item(key = "tabs", contentType = "tabs") {
                        GuideSegmented(
                            options = LogTab.entries.map { it.label },
                            selectedIndex = tab,
                            onSelect = { tab = it },
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        )
                    }
                    item(key = "section-" + tab, contentType = "section") {
                        if (tab == LogTab.LIFE.ordinal) {
                            SectionHeader(
                                "Life list",
                                Modifier.padding(top = 12.dp, bottom = 2.dp),
                                trailing = "${life.size} species",
                            )
                        } else {
                            SectionHeader(
                                "Journal",
                                Modifier.padding(top = 12.dp, bottom = 2.dp),
                                trailing = if (all.size == 1) "1 entry" else "${all.size} entries",
                            )
                        }
                    }

                    if (tab == LogTab.LIFE.ordinal) {
                        items(life, key = { "life-" + it.bird.id }, contentType = { "life" }) { e ->
                            LifeRow(e, onClick = { onOpenBird(e.bird.id) })
                        }
                    } else {
                        items(
                            journal,
                            key = {
                                when (it) {
                                    is JournalRow.Day -> "day-" + it.date
                                    is JournalRow.Entry -> "s-" + it.s.id
                                }
                            },
                            contentType = { if (it is JournalRow.Day) "day" else "entry" },
                        ) { row ->
                            when (row) {
                                is JournalRow.Day -> DayHeader(row.date)
                                is JournalRow.Entry -> SightingRow(
                                    s = row.s,
                                    bird = birdsById[row.s.speciesId],
                                    is24 = is24,
                                    onClick = { onOpenBird(row.s.speciesId) },
                                    onEdit = { editing = row.s },
                                    onDelete = { deleting = row.s },
                                )
                            }
                        }
                    }
                }
            }
        }

        ExtendedFloatingActionButton(
            onClick = onLogSighting,
            icon = { Icon(Icons.Filled.Add, contentDescription = null) },
            text = { Text("Log sighting") },
            containerColor = f.moss,
            contentColor = f.paper,
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        )
    }

    editing?.let { s ->
        SightingEditorSheet(
            initial = s,
            speciesId = s.speciesId,
            onDismiss = { editing = null },
            onSaved = { editing = null },
        )
    }

    quickLog?.let { id ->
        SightingEditorSheet(
            initial = null,
            speciesId = id,
            onDismiss = { quickLog = null },
            onSaved = { quickLog = null },
        )
    }

    deleting?.let { s ->
        val name = birdsById[s.speciesId]?.nameEn ?: "this sighting"
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete entry?") },
            text = {
                Text(
                    "Remove $name on ${s.timeMillis.toLocalDate().format(ShortDayFormat)} from your log? This can't be undone.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val id = s.id
                    deleting = null
                    scope.launch { runCatching { AppGraph.sightings.delete(id) } }
                }) { Text("Delete", color = f.rust) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Keep") } },
            containerColor = f.card,
        )
    }
}

/* ---------- header ---------- */

@Composable
private fun LogHeader(species: Int, total: Int, thisMonth: Int) {
    Column(Modifier.fillMaxWidth()) {
        // The list already insets 16dp; the title adds 4dp to line up with the other tabs.
        GuideScreenTitle(
            title = "My log",
            subtitle = "আমার পাখির খাতা · a birder's notebook",
            horizontalPadding = 4.dp,
        )
        StatsCard(species, total, thisMonth)
        Spacer(Modifier.height(8.dp))
    }
}

/** Three tallies, printed: big serif figures over small-caps labels, split by hairlines. */
@Composable
private fun StatsCard(species: Int, total: Int, thisMonth: Int) {
    val f = Field.colors
    val shape = RoundedCornerShape(10.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(f.card)
            .border(1.dp, f.rule, shape)
            .height(IntrinsicSize.Min)
            .padding(vertical = 14.dp),
    ) {
        Stat(species, "species", Modifier.weight(1f), accent = true)
        VerticalDivider(color = f.rule)
        Stat(total, if (total == 1) "sighting" else "sightings", Modifier.weight(1f))
        VerticalDivider(color = f.rule)
        Stat(thisMonth, "this month", Modifier.weight(1f))
    }
}

@Composable
private fun Stat(value: Int, label: String, modifier: Modifier = Modifier, accent: Boolean = false) {
    val f = Field.colors
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            value.toString(),
            style = MaterialTheme.typography.displaySmall.copy(fontSize = 36.sp, lineHeight = 40.sp),
            color = if (accent) f.moss else f.ink,
            maxLines = 1,
        )
        Text(
            label.uppercase(),
            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.8.sp),
            color = f.inkSoft,
            maxLines = 1,
        )
    }
}

/* ---------- life list ---------- */

@Composable
private fun LifeRow(e: LifeListEntry, onClick: () -> Unit) {
    val f = Field.colors
    RuledCard(
        Modifier
            .fillMaxWidth()
            .clip6()
            .combinedClickableCompat(onClick = onClick),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Thumb(e.bird)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                BirdNames(e.bird, e.bird.id)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PlateNumber(e.bird.sort)
                    Text(
                        "  ·  first seen " + e.firstSeenMillis.toLocalDate().format(ShortDayFormat),
                        style = MaterialTheme.typography.bodySmall, color = f.inkSoft,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    e.sightings.toString(),
                    style = MaterialTheme.typography.headlineSmall.copy(fontFamily = FontFamily.Serif),
                    color = f.ochre,
                )
                Text(
                    (if (e.sightings == 1) "time" else "times").uppercase(),
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.4.sp),
                    color = f.inkSoft,
                )
            }
        }
    }
}

/* ---------- journal ---------- */

@Composable
private fun DayHeader(date: LocalDate) {
    val today = LocalDate.now()
    val extra = when (date) {
        today -> "today"
        today.minusDays(1) -> "yesterday"
        else -> null
    }
    SectionHeader(date.format(DayFormat), Modifier.padding(top = 14.dp, bottom = 2.dp), trailing = extra)
}

@Composable
private fun SightingRow(
    s: Sighting,
    bird: BirdSummary?,
    is24: Boolean,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val f = Field.colors
    var menu by remember { mutableStateOf(false) }
    RuledCard(
        Modifier
            .fillMaxWidth()
            .clip6()
            .combinedClickableCompat(onClick = onClick, onLongClick = { menu = true }),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Thumb(bird, 48.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BirdNames(bird, s.speciesId, Modifier.weight(1f))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        timeText(s.timeMillis.toLocalDateTime(), is24),
                        style = MaterialTheme.typography.bodySmall, color = f.inkSoft,
                    )
                    if (s.count > 1) {
                        Text("  ·  ", style = MaterialTheme.typography.bodySmall, color = f.inkSoft)
                        Text("× ${s.count}", style = MaterialTheme.typography.labelLarge, color = f.ochre)
                    }
                }
                val where = s.place?.takeIf { it.isNotBlank() }
                    ?: if (s.lat != null && s.lon != null) coordsText(s.lat, s.lon) else null
                if (where != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Place, contentDescription = null, tint = f.fern, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(
                            where, style = MaterialTheme.typography.bodySmall, color = f.inkSoft,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                if (s.notes.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        s.notes,
                        style = MaterialTheme.typography.bodyLarge.copy(fontStyle = FontStyle.Italic),
                        color = f.ink, maxLines = 4, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Box {
                IconButton(onClick = { menu = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "More", tint = f.inkSoft)
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, containerColor = f.card) {
                    DropdownMenuItem(
                        text = { Text("Edit") },
                        leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                        onClick = { menu = false; onEdit() },
                    )
                    DropdownMenuItem(
                        text = { Text("Delete", color = f.rust) },
                        leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null, tint = f.rust) },
                        onClick = { menu = false; onDelete() },
                    )
                }
            }
        }
    }
}

/* ---------- empty state ---------- */

@Composable
private fun EmptyLog(onLogSighting: () -> Unit, onLogDoyel: () -> Unit) {
    val f = Field.colors
    RuledCard(Modifier.fillMaxWidth().padding(top = 8.dp), lineSpacing = 30.dp) {
        Spacer(Modifier.height(8.dp))
        Text("A clean page.", style = MaterialTheme.typography.headlineSmall, color = f.ink)
        Spacer(Modifier.height(8.dp))
        Text(
            "Every birder's notebook starts with one bird. Step outside and listen: the doyel " +
                "(দোয়েল, Oriental magpie-robin), Bangladesh's national bird, is probably " +
                "whistling from a rooftop or a mango tree near you.",
            style = MaterialTheme.typography.bodyLarge,
            color = f.ink,
        )
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(
                onClick = onLogDoyel,
                colors = ButtonDefaults.buttonColors(containerColor = f.moss, contentColor = f.paper),
            ) { Text("I saw a doyel") }
            TextButton(onClick = onLogSighting) { Text("Another bird") }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "Your life list and journal will fill in here.",
            style = MaterialTheme.typography.bodySmall, color = f.inkSoft,
            textAlign = TextAlign.Start,
        )
        Spacer(Modifier.height(4.dp))
    }
}

/* ---------- small modifiers ---------- */

private fun Modifier.clip6(): Modifier = this.clip(RoundedCornerShape(6.dp))

@OptIn(ExperimentalFoundationApi::class)
private fun Modifier.combinedClickableCompat(onClick: () -> Unit, onLongClick: (() -> Unit)? = null): Modifier =
    this.combinedClickable(onClick = onClick, onLongClick = onLongClick)
