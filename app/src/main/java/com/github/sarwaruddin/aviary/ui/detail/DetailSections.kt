package com.github.sarwaruddin.aviary.ui.detail

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.sarwaruddin.aviary.model.BdStatus
import com.github.sarwaruddin.aviary.model.Bird
import com.github.sarwaruddin.aviary.model.BirdSummary
import com.github.sarwaruddin.aviary.model.Iucn
import com.github.sarwaruddin.aviary.model.Sighting
import com.github.sarwaruddin.aviary.ui.common.BirdImage
import com.github.sarwaruddin.aviary.ui.common.PlateNumber
import com.github.sarwaruddin.aviary.ui.common.SeasonDot
import com.github.sarwaruddin.aviary.ui.common.SectionHeader
import com.github.sarwaruddin.aviary.ui.theme.Field
import com.github.sarwaruddin.aviary.ui.theme.SciName
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

internal val PageGutter = 20.dp
private val CardShape = RoundedCornerShape(12.dp)

/** A page section: small-caps header with a hairline rule, then the content. */
@Composable
internal fun Section(
    title: String,
    trailing: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = PageGutter).padding(top = 30.dp)) {
        SectionHeader(title, trailing = trailing)
        Spacer(Modifier.height(14.dp))
        content()
    }
}

/** Card surface with a hairline frame, used for every block on the page. */
@Composable
private fun cardModifier(shape: RoundedCornerShape = CardShape): Modifier {
    val f = Field.colors
    return Modifier.clip(shape).background(f.card, shape).border(1.dp, f.rule, shape)
}

// ---------------------------------------------------------------------------------------------
// At a glance

private fun statusLabel(s: BdStatus) = when (s) {
    BdStatus.REGULAR -> "Regular"
    BdStatus.ACCIDENTAL -> "Accidental"
    BdStatus.INTRODUCED -> "Introduced"
    BdStatus.EXTIRPATED -> "Extirpated"
}

private fun statusNote(s: BdStatus) = when (s) {
    BdStatus.REGULAR -> "Occurs regularly"
    BdStatus.ACCIDENTAL -> "Only a few records"
    BdStatus.INTRODUCED -> "Brought in by people"
    BdStatus.EXTIRPATED -> "No longer found here"
}

@Composable
internal fun AtAGlance(bird: Bird) {
    val f = Field.colors
    val s = bird.summary
    val valueStyle = MaterialTheme.typography.titleMedium
    val noteStyle = MaterialTheme.typography.bodySmall
    Section("At a glance") {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            GlanceRow(
                left = {
                    GlanceCell("In Bangladesh", it) {
                        val season = s.season
                        if (season != null) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                SeasonDot(season, size = 9)
                                Spacer(Modifier.width(8.dp))
                                Text(season.label, style = valueStyle, color = f.ink)
                            }
                            Text(season.labelBn, style = MaterialTheme.typography.bodyMedium, color = f.moss)
                        } else {
                            Text(statusLabel(s.status), style = valueStyle, color = f.ink)
                            Text(statusNote(s.status), style = noteStyle, color = f.inkSoft)
                        }
                    }
                },
                right = {
                    GlanceCell("Red List", it) {
                        val i = s.iucn ?: Iucn.NE
                        val c = if (i.threatened) f.rust else f.ink
                        Text(i.label, style = valueStyle, color = c)
                        Text(
                            "IUCN · " + i.code,
                            style = noteStyle,
                            color = if (i.threatened) f.rust else f.inkSoft,
                        )
                    }
                },
            )
            GlanceRow(
                left = {
                    GlanceCell("Family", it) {
                        val fam = bird.family
                        if (fam.commonName.isNotBlank()) {
                            Text(fam.commonName, style = valueStyle, color = f.ink)
                            Text(fam.name, style = noteStyle.merge(SciName), color = f.inkSoft)
                        } else {
                            Text(fam.name, style = valueStyle.merge(SciName), color = f.ink)
                        }
                    }
                },
                right = {
                    GlanceCell("Order", it) {
                        Text(bird.family.order, style = valueStyle, color = f.ink,
                            maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                },
            )
        }
    }
}

@Composable
private fun GlanceRow(
    left: @Composable (Modifier) -> Unit,
    right: @Composable (Modifier) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        left(Modifier.weight(1f).fillMaxHeight())
        right(Modifier.weight(1f).fillMaxHeight())
    }
}

@Composable
private fun GlanceCell(label: String, modifier: Modifier, content: @Composable ColumnScope.() -> Unit) {
    val f = Field.colors
    Column(
        modifier
            .then(cardModifier())
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(
            label.uppercase(),
            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.6.sp),
            color = f.inkSoft,
        )
        Spacer(Modifier.height(3.dp))
        content()
    }
}

// ---------------------------------------------------------------------------------------------
// Field notes

@Composable
internal fun FieldNotes(facts: List<String>) {
    val f = Field.colors
    Section("Field notes") {
        Column(cardModifier().fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
            facts.forEachIndexed { i, fact ->
                if (i > 0) HorizontalDivider(thickness = 1.dp, color = f.rule.copy(alpha = 0.7f))
                Row(Modifier.padding(vertical = 12.dp)) {
                    Text(
                        "${i + 1}",
                        style = MaterialTheme.typography.titleLarge,
                        color = f.ochre,
                        modifier = Modifier.width(28.dp).alignByBaseline(),
                    )
                    Text(
                        fact,
                        style = MaterialTheme.typography.bodyLarge,
                        color = f.ink,
                        modifier = Modifier.weight(1f).alignByBaseline(),
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// About

internal fun wikiUrl(host: String, title: String) =
    "https://$host/wiki/" + Uri.encode(title.replace(' ', '_'))

@Composable
internal fun About(bird: Bird) {
    val f = Field.colors
    val context = LocalContext.current
    val hasEn = !bird.summaryEn.isNullOrBlank()
    val hasBn = !bird.summaryBn.isNullOrBlank()
    var bangla by remember { mutableStateOf(!hasEn && hasBn) }
    var expanded by remember { mutableStateOf(false) }
    val text = if (bangla) bird.summaryBn else bird.summaryEn
    val paras = remember(text) {
        text.orEmpty().split("\n\n").map { it.trim() }.filter { it.isNotEmpty() }
    }
    val shown = if (expanded) paras else paras.take(2)
    val url = when {
        bangla && bird.wikiBn != null -> wikiUrl("bn.wikipedia.org", bird.wikiBn)
        bird.wikiEn != null -> wikiUrl("en.wikipedia.org", bird.wikiEn)
        bird.wikiBn != null -> wikiUrl("bn.wikipedia.org", bird.wikiBn)
        else -> null
    }

    Column(Modifier.fillMaxWidth().padding(horizontal = PageGutter).padding(top = 30.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionHeader("About", Modifier.weight(1f))
            if (hasEn && hasBn) {
                Spacer(Modifier.width(12.dp))
                LangToggle(bangla) { bangla = it; expanded = false }
            }
        }
        Spacer(Modifier.height(14.dp))
        shown.forEachIndexed { i, p ->
            if (i > 0) Spacer(Modifier.height(12.dp))
            Text(p, style = MaterialTheme.typography.bodyLarge, color = f.ink)
        }
        Row(
            Modifier.fillMaxWidth().padding(top = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "From Wikipedia · CC BY-SA",
                style = MaterialTheme.typography.bodySmall,
                color = f.inkSoft,
                textDecoration = if (url != null) TextDecoration.Underline else null,
                modifier = Modifier
                    .weight(1f)
                    .then(if (url != null) Modifier.clickable { context.openUrl(url) } else Modifier)
                    .padding(vertical = 8.dp),
            )
            if (paras.size > 2) {
                TextButton(onClick = { expanded = !expanded }) {
                    Text(
                        if (expanded) "Less" else "More",
                        style = MaterialTheme.typography.labelLarge,
                        color = f.moss,
                    )
                }
            }
        }
    }
}

/** English / বাংলা segmented pill. */
@Composable
private fun LangToggle(bangla: Boolean, onChange: (Boolean) -> Unit) {
    val f = Field.colors
    val pill = RoundedCornerShape(50)
    Row(
        Modifier
            .clip(pill)
            .background(f.card, pill)
            .border(1.dp, f.rule, pill)
            .padding(2.dp),
    ) {
        Segment("English", !bangla) { onChange(false) }
        Segment("বাংলা", bangla) { onChange(true) }
    }
}

@Composable
private fun Segment(text: String, selected: Boolean, onClick: () -> Unit) {
    val f = Field.colors
    val pill = RoundedCornerShape(50)
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = if (selected) f.paper else f.inkSoft,
        modifier = Modifier
            .clip(pill)
            .background(if (selected) f.moss else Color.Transparent, pill)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 5.dp),
    )
}

// ---------------------------------------------------------------------------------------------
// Sightings

private val dateFormat: DateTimeFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)

private fun formatDate(millis: Long): String =
    Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate().format(dateFormat)

@Composable
internal fun SightingsBlock(sightings: List<Sighting>?, onLogSighting: () -> Unit) {
    val f = Field.colors
    Section("Your sightings") {
        Row(
            cardModifier().fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            when {
                sightings == null -> Text("…", style = MaterialTheme.typography.bodyLarge, color = f.inkSoft)
                sightings.isEmpty() -> Column {
                    Text("Not in your log yet", style = MaterialTheme.typography.titleMedium, color = f.ink)
                    Text(
                        "When you spot one, log it here.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = f.inkSoft,
                    )
                }
                else -> {
                    val last = sightings.maxBy { it.timeMillis }
                    val n = sightings.size
                    Text(
                        "$n",
                        style = MaterialTheme.typography.displaySmall,
                        color = f.moss,
                    )
                    Spacer(Modifier.width(14.dp))
                    Box(Modifier.width(1.dp).height(40.dp).background(f.rule))
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (n == 1) "sighting" else "sightings",
                            style = MaterialTheme.typography.titleMedium,
                            color = f.ink,
                        )
                        Text(
                            "Last seen " + formatDate(last.timeMillis) +
                                (last.place?.takeIf { it.isNotBlank() }?.let { ", $it" } ?: ""),
                            style = MaterialTheme.typography.bodyMedium,
                            color = f.inkSoft,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = onLogSighting,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = f.moss, contentColor = f.paper),
        ) {
            Icon(Icons.Filled.Add, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Log a sighting", style = MaterialTheme.typography.labelLarge)
        }
    }
}

// ---------------------------------------------------------------------------------------------
// More from this family

@Composable
internal fun Relatives(birds: List<BirdSummary>, total: Int, onOpenBird: (String) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 30.dp)) {
        SectionHeader("More from this family", Modifier.padding(horizontal = PageGutter), trailing = "$total")
        Spacer(Modifier.height(14.dp))
        LazyRow(
            contentPadding = PaddingValues(horizontal = PageGutter),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(birds, key = { it.id }) { b -> PlateCard(b) { onOpenBird(b.id) } }
        }
    }
}

@Composable
private fun PlateCard(b: BirdSummary, onClick: () -> Unit) {
    val f = Field.colors
    val inner = RoundedCornerShape(8.dp)
    Column(
        Modifier
            .width(140.dp)
            .then(cardModifier())
            .clickable(onClick = onClick)
            .padding(6.dp),
    ) {
        BirdImage(
            b.thumb,
            contentDescription = b.nameEn,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(4f / 5f)
                .clip(inner)
                .border(0.5.dp, f.rule, inner),
            maxPx = 300,
        )
        Column(Modifier.padding(start = 4.dp, end = 4.dp, top = 8.dp, bottom = 4.dp)) {
            PlateNumber(b.sort, color = f.ochre)
            Text(
                b.nameEn,
                style = MaterialTheme.typography.titleSmall.copy(fontFamily = FontFamily.Serif),
                color = f.ink,
                maxLines = 2,
                minLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                b.nameBn?.takeIf { it.isNotBlank() } ?: " ",
                style = MaterialTheme.typography.bodySmall,
                color = f.moss,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Further reading

@Composable
internal fun Links(bird: Bird) {
    val f = Field.colors
    val context = LocalContext.current
    Section("Further reading") {
        Column(cardModifier().fillMaxWidth()) {
            var first = true
            bird.ebirdCode?.let { code ->
                first = false
                val url = "https://ebird.org/species/$code"
                LinkRow(Icons.Filled.Place, "eBird", "Maps, sightings and sounds · ebird.org") {
                    context.openUrl(url)
                }
            }
            bird.wikiEn?.let { t ->
                if (!first) HorizontalDivider(thickness = 1.dp, color = f.rule.copy(alpha = 0.7f))
                val url = wikiUrl("en.wikipedia.org", t)
                LinkRow(Icons.Filled.Info, "Wikipedia", "The full article · en.wikipedia.org") {
                    context.openUrl(url)
                }
            }
        }
    }
}

@Composable
private fun LinkRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    val f = Field.colors
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(36.dp).background(f.moss.copy(alpha = 0.12f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = f.moss, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = f.ink)
            Text(
                subtitle, style = MaterialTheme.typography.bodySmall, color = f.inkSoft,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        Text("→", style = MaterialTheme.typography.titleMedium, color = f.inkSoft)
    }
}
