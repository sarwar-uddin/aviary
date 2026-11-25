package com.github.sarwaruddin.aviary.ui.common

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.sarwaruddin.aviary.ui.theme.Field

/**
 * v2 controls shared by the tab screens: screen titles, pill chips, a pill segmented
 * toggle and a gentle press-scale for plate cards. See docs/DESIGN.md.
 */

val GuidePillShape = RoundedCornerShape(50)

/**
 * A tab screen's title block: optional small-caps kicker, a big serif title and a
 * subtitle that wraps instead of truncating.
 */
@Composable
fun GuideScreenTitle(
    title: String,
    subtitle: String?,
    modifier: Modifier = Modifier,
    kicker: String? = null,
    horizontalPadding: Dp = 20.dp,
) {
    val f = Field.colors
    Column(modifier.fillMaxWidth().padding(start = horizontalPadding, end = horizontalPadding, top = 20.dp, bottom = 12.dp)) {
        if (kicker != null) {
            Text(
                kicker.uppercase(),
                style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 2.sp),
                color = f.moss,
                modifier = Modifier.padding(bottom = 2.dp),
            )
        }
        Text(
            title,
            style = MaterialTheme.typography.displaySmall.copy(fontSize = 42.sp, lineHeight = 48.sp),
            color = f.ink,
        )
        if (subtitle != null) {
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp, lineHeight = 23.sp),
                color = f.inkSoft,
            )
        }
    }
}

/**
 * Pill chip. Selected: moss fill with paper text. Unselected: a hairline outline on the page.
 * [arrow] adds a small drop-down chevron (for chips that open a sheet).
 */
@Composable
fun GuideChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    dot: Color? = null,
    arrow: Boolean = false,
    maxLabelWidth: Dp = 200.dp,
) {
    val f = Field.colors
    val fg = if (selected) f.paper else f.ink
    Row(
        modifier
            .height(34.dp)
            .clip(GuidePillShape)
            .then(
                if (selected) Modifier.background(f.moss)
                else Modifier.border(1.dp, f.rule, GuidePillShape),
            )
            .semantics { this.selected = selected }
            .clickable(role = Role.Button, onClick = onClick)
            .padding(start = if (dot != null) 12.dp else 14.dp, end = if (arrow) 10.dp else 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        if (dot != null) {
            androidx.compose.foundation.layout.Box(
                Modifier.size(7.dp).background(if (selected) f.paper else dot, androidx.compose.foundation.shape.CircleShape),
            )
        }
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = fg,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = maxLabelWidth),
        )
        if (arrow) {
            Icon(
                Icons.Filled.KeyboardArrowDown, contentDescription = null,
                tint = if (selected) f.paper else f.inkSoft, modifier = Modifier.size(18.dp),
            )
        }
    }
}

/** A hairline pill split into segments; the selected one is moss-filled. */
@Composable
fun GuideSegmented(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val f = Field.colors
    Row(
        modifier
            .height(38.dp)
            .clip(GuidePillShape)
            .border(1.dp, f.rule, GuidePillShape)
            .padding(3.dp),
    ) {
        options.forEachIndexed { i, label ->
            val sel = i == selectedIndex
            Row(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .height(32.dp)
                    .clip(GuidePillShape)
                    .then(if (sel) Modifier.background(f.moss) else Modifier)
                    .semantics { this.selected = sel }
                    .clickable(role = Role.Tab) { onSelect(i) },
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (sel) f.paper else f.inkSoft,
                    maxLines = 1,
                )
            }
        }
    }
}

/**
 * Clickable for plate cards: a calm scale-down while pressed, no ripple.
 * Put it first in the chain so the whole card (frame included) scales.
 */
fun Modifier.plateClickable(onClick: () -> Unit): Modifier = composed {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.97f else 1f, tween(140), label = "platePress")
    Modifier
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .clickable(interactionSource = source, indication = null, role = Role.Button, onClick = onClick)
}
