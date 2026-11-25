package com.github.sarwaruddin.aviary.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.sarwaruddin.aviary.model.Season
import com.github.sarwaruddin.aviary.ui.theme.Field

/** Printed-guide details shared by all screens. See docs/DESIGN.md. */

/** Small-caps section title with a hairline rule, e.g. "FIELD NOTES ——————". */
@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, trailing: String? = null) {
    val f = Field.colors
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title.uppercase(), style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 2.sp), color = f.moss)
        Spacer(Modifier.width(12.dp))
        HorizontalDivider(Modifier.weight(1f), thickness = 1.dp, color = f.rule)
        if (trailing != null) {
            Spacer(Modifier.width(12.dp))
            Text(trailing, style = MaterialTheme.typography.labelMedium, color = f.inkSoft)
        }
    }
}

/** A centred ornament between two rules: ——— ❦ ———. */
@Composable
fun OrnamentDivider(modifier: Modifier = Modifier, color: Color = Field.colors.rule) {
    Row(
        modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        HorizontalDivider(Modifier.width(56.dp), thickness = 1.dp, color = color)
        Text("❦", color = Field.colors.ochre, fontFamily = FontFamily.Serif, fontSize = 16.sp,
            modifier = Modifier.padding(horizontal = 10.dp))
        HorizontalDivider(Modifier.width(56.dp), thickness = 1.dp, color = color)
    }
}

/** "No. 706": the species' place in the guide (sort + 1). */
@Composable
fun PlateNumber(sort: Int, modifier: Modifier = Modifier, color: Color = Field.colors.inkSoft) {
    Text(
        "No. ${sort + 1}",
        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Serif, fontStyle = FontStyle.Italic, letterSpacing = 0.5.sp),
        color = color, modifier = modifier,
    )
}

/** A small filled dot in the season's colour. */
@Composable
fun SeasonDot(season: Season?, modifier: Modifier = Modifier, size: Int = 8) {
    val c = season?.let { seasonColor(it) } ?: Field.colors.rule
    Box(modifier.size(size.dp).background(c, CircleShape))
}

/** Thin horizontal proportion bar, e.g. residents vs winter vs summer this month. */
@Composable
fun ProportionBar(parts: List<Pair<Color, Int>>, modifier: Modifier = Modifier, height: Int = 6) {
    val total = parts.sumOf { it.second }.coerceAtLeast(1)
    Canvas(modifier.fillMaxWidth().height(height.dp)) {
        var x = 0f
        parts.forEach { (c, n) ->
            val w = size.width * n / total
            if (w > 0f) drawLine(c, Offset(x, size.height / 2), Offset(x + w, size.height / 2), strokeWidth = size.height)
            x += w
        }
    }
}
