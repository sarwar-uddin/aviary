package com.github.sarwaruddin.aviary.ui.log

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.github.sarwaruddin.aviary.model.BirdSummary
import com.github.sarwaruddin.aviary.ui.common.BirdImage
import com.github.sarwaruddin.aviary.ui.common.SciNameText
import com.github.sarwaruddin.aviary.ui.theme.Field
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

/* ---------- formatting ---------- */

internal val DayFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE d MMM yyyy", Locale.ENGLISH)
internal val ShortDayFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)
private val Time24: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH)
private val Time12: DateTimeFormatter = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)

internal fun Long.toLocalDateTime(): LocalDateTime =
    LocalDateTime.ofInstant(Instant.ofEpochMilli(this), ZoneId.systemDefault())

internal fun Long.toLocalDate(): LocalDate = toLocalDateTime().toLocalDate()

internal fun LocalDateTime.toMillis(): Long = atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

internal fun timeText(dt: LocalDateTime, is24: Boolean): String = dt.format(if (is24) Time24 else Time12)

/** "23.8103° N, 90.4125° E" (rounded to 4 decimals). */
internal fun coordsText(lat: Double, lon: Double): String {
    val ns = if (lat >= 0) "N" else "S"
    val ew = if (lon >= 0) "E" else "W"
    return String.format(Locale.US, "%.4f° %s, %.4f° %s", abs(lat), ns, abs(lon), ew)
}

/* ---------- notebook pieces ---------- */

/**
 * A notebook card: card-coloured paper, a hairline border, faint ruled lines
 * and a red margin line on the left, like a page from a field notebook.
 */
@Composable
internal fun RuledCard(
    modifier: Modifier = Modifier,
    lineSpacing: Dp = 26.dp,
    margin: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val f = Field.colors
    val shape = RoundedCornerShape(6.dp)
    Column(
        modifier
            .clip(shape)
            .background(f.card)
            .drawBehind {
                val step = lineSpacing.toPx()
                val stroke = 1.dp.toPx()
                var y = step
                val lineColor = f.rule.copy(alpha = 0.45f)
                while (y < size.height) {
                    drawLine(lineColor, Offset(0f, y), Offset(size.width, y), stroke)
                    y += step
                }
                if (margin) {
                    val x = 10.dp.toPx()
                    drawLine(f.rust.copy(alpha = 0.35f), Offset(x, 0f), Offset(x, size.height), stroke)
                }
            }
            .border(1.dp, f.rule, shape)
            .padding(start = if (margin) 18.dp else 12.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
        content = content,
    )
}

@Composable
internal fun Thumb(bird: BirdSummary?, size: Dp = 52.dp) {
    BirdImage(
        ref = bird?.thumb,
        contentDescription = null,
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(6.dp))
            .background(Field.colors.rule.copy(alpha = 0.5f))
            .border(1.dp, Field.colors.rule, RoundedCornerShape(6.dp)),
        contentScale = ContentScale.Crop,
        maxPx = 200,
    )
}

/** English name, then Bangla name, then the italic binomial. */
@Composable
internal fun BirdNames(bird: BirdSummary?, fallbackId: String, modifier: Modifier = Modifier) {
    val f = Field.colors
    Column(modifier) {
        Text(
            bird?.nameEn ?: fallbackId,
            style = MaterialTheme.typography.titleMedium,
            color = f.ink, maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        val bn = bird?.nameBn
        if (bird != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!bn.isNullOrBlank()) {
                    Text(
                        bn, style = MaterialTheme.typography.bodyMedium, color = f.moss,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.width(8.dp))
                }
                SciNameText(bird.nameSci)
            }
        }
    }
}
