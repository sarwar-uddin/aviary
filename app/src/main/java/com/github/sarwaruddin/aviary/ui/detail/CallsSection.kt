package com.github.sarwaruddin.aviary.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.github.sarwaruddin.aviary.data.AudioPlayer
import com.github.sarwaruddin.aviary.model.ImageRef
import com.github.sarwaruddin.aviary.model.Recording
import com.github.sarwaruddin.aviary.ui.common.BirdImage
import com.github.sarwaruddin.aviary.ui.theme.Field

/** "Calls & songs": up to three recordings, each a sonogram strip with a play button and credit. */
@Composable
internal fun CallsSection(recordings: List<Recording>) {
    val state by AudioPlayer.state.collectAsState()
    Section("Calls & songs", trailing = if (recordings.size > 1) "${recordings.size}" else null) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            recordings.forEach { r -> RecordingCard(r, if (state.url == r.url) state else null) }
        }
    }
}

@Composable
private fun RecordingCard(r: Recording, state: AudioPlayer.State?) {
    val f = Field.colors
    val ctx = LocalContext.current
    val shape = RoundedCornerShape(12.dp)
    val status = state?.status ?: AudioPlayer.Status.IDLE
    Column(
        Modifier.fillMaxWidth().clip(shape).background(f.card, shape).border(1.dp, f.rule, shape)
            .clickable { AudioPlayer.toggle(r.url) }
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PlayButton(status)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    (r.type ?: "Recording").replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.titleMedium, color = f.ink,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                val where = listOfNotNull(r.length, r.country).joinToString(" · ")
                val sub = when (status) {
                    AudioPlayer.Status.LOADING -> "Loading…"
                    AudioPlayer.Status.ERROR -> "Couldn't play this one. Are you online?"
                    else -> where
                }
                if (sub.isNotEmpty()) Text(sub, style = MaterialTheme.typography.bodySmall,
                    color = if (status == AudioPlayer.Status.ERROR) f.rust else f.inkSoft)
            }
        }
        if (r.sonogram != null) {
            Spacer(Modifier.height(10.dp))
            Box(Modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(6.dp)).background(f.paper)) {
                BirdImage(ImageRef(null, r.sonogram), contentDescription = "Sonogram",
                    modifier = Modifier.fillMaxSize().alpha(0.85f), contentScale = ContentScale.FillBounds, maxPx = 600)
                val progress = if (state != null && state.durationMs > 0) state.positionMs / state.durationMs.toFloat() else 0f
                if (status == AudioPlayer.Status.PLAYING) {
                    Box(Modifier.fillMaxHeight().fillMaxWidth(progress.coerceIn(0f, 1f)).background(f.moss.copy(alpha = 0.18f)))
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        val c = r.credit
        Text(
            listOfNotNull(c.author?.let { "Recording: $it" }, c.license,
                if (c.source == "xeno-canto") "xeno-canto" else "Wikimedia Commons").joinToString(" · "),
            style = MaterialTheme.typography.bodySmall, color = f.inkSoft,
            maxLines = 2, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.clickable(enabled = c.pageUrl != null) { c.pageUrl?.let { ctx.openUrl(it) } },
        )
    }
}

@Composable
private fun PlayButton(status: AudioPlayer.Status) {
    val f = Field.colors
    Box(Modifier.size(44.dp).background(f.moss, CircleShape), contentAlignment = Alignment.Center) {
        when (status) {
            AudioPlayer.Status.LOADING -> CircularProgressIndicator(color = f.paper, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
            AudioPlayer.Status.PLAYING -> Box(Modifier.size(14.dp).background(f.paper, RoundedCornerShape(2.dp)))  // stop square
            else -> Icon(Icons.Filled.PlayArrow, contentDescription = "Play", tint = f.paper)
        }
    }
}
