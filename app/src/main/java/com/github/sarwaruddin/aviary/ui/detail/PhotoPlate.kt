package com.github.sarwaruddin.aviary.ui.detail

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.github.sarwaruddin.aviary.model.Bird
import com.github.sarwaruddin.aviary.model.Credit
import com.github.sarwaruddin.aviary.model.Photo
import com.github.sarwaruddin.aviary.ui.common.BirdImage
import com.github.sarwaruddin.aviary.ui.common.PlateNumber
import com.github.sarwaruddin.aviary.ui.theme.Field
import com.github.sarwaruddin.aviary.ui.theme.SciName

/** How far the names block rides up into the hero's paper fade. */
internal val NameOverlap = 40.dp

/** Open [url] in the browser; silently ignore if nothing can handle it. */
internal fun Context.openUrl(url: String) {
    try {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: Exception) {
        // no browser; nothing to do
    }
}

internal fun creditLine(c: Credit): String = buildString {
    append("Photo: ")
    append(c.author?.takeIf { it.isNotBlank() } ?: "unknown")
    c.license?.takeIf { it.isNotBlank() }?.let { append(" · "); append(it) }
}

private fun sourceName(c: Credit) = when (c.source) {
    "commons" -> "Wikimedia Commons"
    "xeno-canto" -> "xeno-canto"
    else -> c.source
}

/** Lay the content out [by] higher than normal, overlapping whatever sits above it. */
internal fun Modifier.pullUp(by: Dp): Modifier = layout { measurable, constraints ->
    val p = measurable.measure(constraints)
    val px = by.roundToPx()
    layout(p.width, (p.height - px).coerceAtLeast(0)) { p.place(0, -px) }
}

/**
 * One photo: the small (bundled) thumbnail drawn first as an instant frame,
 * with the large image on top once it arrives.
 */
@Composable
internal fun PlateImage(
    photo: Photo,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
) {
    Box(modifier) {
        if (photo.small.asset != null) {
            BirdImage(photo.small, null, Modifier.fillMaxSize(), contentScale = contentScale, maxPx = 360)
        }
        BirdImage(
            photo.large, contentDescription, Modifier.fillMaxSize(),
            contentScale = contentScale, maxPx = 1100, placeholder = photo.small.asset == null,
        )
    }
}

/** Paper-tinted round back button, for floating over a photo. */
@Composable
internal fun FloatingBack(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val f = Field.colors
    Box(
        modifier
            .size(42.dp)
            .clip(CircleShape)
            .background(f.paper.copy(alpha = 0.85f))
            .border(1.dp, f.rule, CircleShape)
            .clickable(onClick = onBack),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = f.ink)
    }
}

/** The paper fade at the bottom of a hero, so the page flows out of the photo. */
@Composable
internal fun HeroFade(modifier: Modifier = Modifier) {
    val paper = Field.colors.paper
    Box(
        modifier.background(
            Brush.verticalGradient(
                0f to Color.Transparent,
                0.45f to paper.copy(alpha = 0.45f),
                0.75f to paper.copy(alpha = 0.92f),
                1f to paper,
            ),
        ),
    )
}

/** A soft paper wash under the status bar so its icons stay readable over a photo. */
@Composable
internal fun StatusScrim(modifier: Modifier = Modifier) {
    val paper = Field.colors.paper
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    Box(
        modifier
            .fillMaxWidth()
            .height(top + 40.dp)
            .background(Brush.verticalGradient(listOf(paper.copy(alpha = 0.55f), Color.Transparent))),
    )
}

/**
 * Full-bleed photo pager, drawn under the status bar, with a half-speed parallax
 * (read from [list] in the draw phase), a paper fade and page dots.
 */
@Composable
internal fun Hero(
    bird: Bird,
    pager: PagerState,
    list: LazyListState,
    height: Dp,
    onOpenPhoto: (Int) -> Unit,
) {
    val f = Field.colors
    val photos = bird.photos
    val name = bird.summary.nameEn
    Box(
        Modifier
            .fillMaxWidth()
            .height(height)
            .clipToBounds()
            .background(f.card),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationY = if (list.firstVisibleItemIndex == 0) list.firstVisibleItemScrollOffset * 0.5f else 0f
                },
        ) {
            if (photos.isEmpty()) {
                BirdImage(bird.summary.thumb, name, Modifier.fillMaxSize(), maxPx = 360)
            } else {
                HorizontalPager(state = pager, modifier = Modifier.fillMaxSize()) { page ->
                    PlateImage(photos[page], name, Modifier.fillMaxSize().clickable { onOpenPhoto(page) })
                }
            }
        }
        StatusScrim(Modifier.align(Alignment.TopCenter))
        HeroFade(Modifier.align(Alignment.BottomCenter).fillMaxWidth().fillMaxHeight(0.42f))
        if (photos.size > 1) {
            PageDots(
                count = photos.size,
                current = pager.currentPage,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = NameOverlap + 22.dp),
            )
        }
    }
}

@Composable
private fun PageDots(count: Int, current: Int, modifier: Modifier = Modifier) {
    val f = Field.colors
    Row(
        modifier
            .clip(RoundedCornerShape(50))
            .background(f.paper.copy(alpha = 0.7f))
            .padding(horizontal = 9.dp, vertical = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(count) { i ->
            val on = i == current
            Box(
                Modifier
                    .size(if (on) 7.dp else 5.dp)
                    .background(if (on) f.moss else f.ink.copy(alpha = 0.3f), CircleShape),
            )
        }
    }
}

/**
 * Plate number and photo credit on one line, then the English name (serif display),
 * the Bangla name large in moss and the Latin name in italics.
 */
@Composable
internal fun NamesBlock(bird: Bird, credit: Credit?, modifier: Modifier = Modifier) {
    val f = Field.colors
    val context = LocalContext.current
    val s = bird.summary
    Column(modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PlateNumber(s.sort, color = f.ochre)
            Spacer(Modifier.width(16.dp))
            if (credit != null) {
                val url = credit.pageUrl
                Text(
                    creditLine(credit),
                    style = MaterialTheme.typography.bodySmall,
                    color = f.inkSoft,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.End,
                    modifier = Modifier
                        .weight(1f)
                        .then(if (url != null) Modifier.clickable { context.openUrl(url) } else Modifier)
                        .padding(vertical = 4.dp),
                )
            } else {
                Spacer(Modifier.weight(1f))
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(s.nameEn, style = MaterialTheme.typography.displaySmall, color = f.ink)
        s.nameBn?.takeIf { it.isNotBlank() }?.let {
            Spacer(Modifier.height(2.dp))
            Text(it, style = MaterialTheme.typography.headlineMedium, color = f.moss)
        }
        Spacer(Modifier.height(2.dp))
        Text(s.nameSci, style = MaterialTheme.typography.titleMedium.merge(SciName), color = f.inkSoft)
    }
}

/** Full-screen viewer with pinch-zoom, pan and double-tap; shows the full credit. */
@Composable
internal fun PhotoViewer(photo: Photo, name: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        var scale by remember { mutableFloatStateOf(1f) }
        var offset by remember { mutableStateOf(Offset.Zero) }
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            PlateImage(
                photo,
                name,
                Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures(onDoubleTap = {
                            if (scale > 1.01f) { scale = 1f; offset = Offset.Zero } else scale = 2.5f
                        })
                    }
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            val s = (scale * zoom).coerceIn(1f, 5f)
                            scale = s
                            offset = if (s <= 1f) Offset.Zero else {
                                val maxX = size.width * (s - 1f) / 2f
                                val maxY = size.height * (s - 1f) / 2f
                                val n = offset + pan
                                Offset(n.x.coerceIn(-maxX, maxX), n.y.coerceIn(-maxY, maxY))
                            }
                        }
                    }
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offset.x
                        translationY = offset.y
                    },
                contentScale = ContentScale.Fit,
            )

            Box(Modifier.fillMaxSize().systemBarsPadding()) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp)
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.5f))
                        .clickable(onClick = onDismiss),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.Close, contentDescription = "Close", tint = Color.White)
                }

                val c = photo.credit
                Column(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.6f))
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                ) {
                    val soft = Color.White.copy(alpha = 0.72f)
                    Text(name, style = MaterialTheme.typography.titleMedium, color = Color.White)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Photo: " + (c.author?.takeIf { it.isNotBlank() } ?: "unknown"),
                        style = MaterialTheme.typography.bodyMedium, color = soft,
                    )
                    c.license?.takeIf { it.isNotBlank() }?.let { lic ->
                        val lu = c.licenseUrl
                        Text(
                            "License: $lic",
                            style = MaterialTheme.typography.bodyMedium,
                            color = soft,
                            textDecoration = if (lu != null) TextDecoration.Underline else null,
                            modifier = if (lu != null) Modifier.clickable { context.openUrl(lu) } else Modifier,
                        )
                    }
                    c.pageUrl?.let { pu ->
                        Text(
                            "View source on " + sourceName(c),
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White,
                            textDecoration = TextDecoration.Underline,
                            modifier = Modifier.padding(top = 2.dp).clickable { context.openUrl(pu) },
                        )
                    }
                }
            }
        }
    }
}
