package com.github.sarwaruddin.aviary.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.github.sarwaruddin.aviary.model.BdStatus
import com.github.sarwaruddin.aviary.model.Iucn
import com.github.sarwaruddin.aviary.model.Season
import com.github.sarwaruddin.aviary.ui.theme.Field
import com.github.sarwaruddin.aviary.ui.theme.SciName

/** Shared small pieces. Any screen may use these; add new shared bits in new files. */

@Composable
fun Tag(text: String, color: Color, modifier: Modifier = Modifier, filled: Boolean = false) {
    val shape = RoundedCornerShape(4.dp)
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = if (filled) Field.colors.paper else color,
        maxLines = 1,
        modifier = modifier
            .then(if (filled) Modifier.background(color, shape) else Modifier.border(1.dp, color.copy(alpha = 0.7f), shape))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

@Composable
fun seasonColor(s: Season): Color = when (s) {
    Season.RESIDENT -> Field.colors.moss
    Season.WINTER -> Field.colors.sky
    Season.SUMMER -> Field.colors.ochre
    Season.PASSAGE -> Field.colors.fern
    Season.VAGRANT -> Field.colors.inkSoft
}

@Composable
fun SeasonTag(s: Season, modifier: Modifier = Modifier) = Tag(s.label, seasonColor(s), modifier)

/** Only shown for NT and threatened categories; LC is the default and gets no badge. */
@Composable
fun IucnTag(i: Iucn, modifier: Modifier = Modifier) {
    if (i == Iucn.LC || i == Iucn.NE) return
    Tag(i.code + " · " + i.label, if (i.threatened) Field.colors.rust else Field.colors.ochre, modifier, filled = i.threatened)
}

@Composable
fun StatusTag(s: BdStatus, modifier: Modifier = Modifier) {
    if (s == BdStatus.REGULAR) return
    Tag(s.name.lowercase(), Field.colors.inkSoft, modifier)
}

@Composable
fun SciNameText(name: String, modifier: Modifier = Modifier) = Text(
    name, style = MaterialTheme.typography.bodyMedium.merge(SciName),
    color = Field.colors.inkSoft, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = modifier,
)
