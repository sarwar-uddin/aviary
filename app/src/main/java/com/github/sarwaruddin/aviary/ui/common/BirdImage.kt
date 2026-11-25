package com.github.sarwaruddin.aviary.ui.common

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.github.sarwaruddin.aviary.data.ImageLoader
import com.github.sarwaruddin.aviary.model.ImageRef
import com.github.sarwaruddin.aviary.ui.theme.Field

/**
 * A bird picture from an [ImageRef] (bundled asset first, then the network).
 * Shows a soft paper card with a faint feather while loading or when there's no image,
 * and fades the photo in once it arrives. Size it with [modifier].
 */
@Composable
fun BirdImage(
    ref: ImageRef?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    maxPx: Int = 1024,
    placeholder: Boolean = true,   // false: transparent while loading (to layer over a thumbnail)
) {
    val image by produceState<ImageBitmap?>(
        initialValue = ref?.let { ImageLoader.cached(it, maxPx) },
        ref, maxPx,
    ) {
        // produceState's state outlives key changes, so reset it for the new ref.
        val hit = ref?.let { ImageLoader.cached(it, maxPx) }
        value = hit
        if (hit == null && ref != null) value = ImageLoader.load(ref, maxPx)
    }
    val alpha by animateFloatAsState(if (image != null) 1f else 0f, tween(350), label = "birdImageFade")
    val f = Field.colors

    Box(
        modifier
            .then(if (placeholder) Modifier.background(f.card) else Modifier)
            .semantics { if (contentDescription != null) this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        if (placeholder && alpha < 1f) Feather(Modifier.fillMaxWidth(0.32f).aspectRatio(1f), f.rule)
        image?.let {
            Image(
                bitmap = it,
                contentDescription = null,   // described on the Box, so placeholder and photo read the same
                contentScale = contentScale,
                modifier = Modifier.fillMaxSize().graphicsLayer { this.alpha = alpha },
            )
        }
    }
}

/** A simple quill: a tilted vane and shaft, drawn in a faint hairline colour. */
@Composable
private fun Feather(modifier: Modifier, color: androidx.compose.ui.graphics.Color) {
    Canvas(modifier) {
        val w = size.minDimension
        rotate(-35f) {
            val cx = size.width / 2
            val top = center.y - w * 0.45f
            drawOval(
                color = color.copy(alpha = 0.55f),
                topLeft = Offset(cx - w * 0.16f, top),
                size = Size(w * 0.32f, w * 0.72f),
            )
            drawLine(
                color = color,
                start = Offset(cx, top + w * 0.04f),
                end = Offset(cx, center.y + w * 0.45f),
                strokeWidth = w * 0.025f,
                cap = StrokeCap.Round,
            )
        }
    }
}
