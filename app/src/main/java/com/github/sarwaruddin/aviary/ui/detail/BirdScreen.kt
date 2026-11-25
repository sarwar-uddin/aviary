package com.github.sarwaruddin.aviary.ui.detail

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.github.sarwaruddin.aviary.data.AppGraph
import com.github.sarwaruddin.aviary.model.Bird
import com.github.sarwaruddin.aviary.model.BirdFilter
import com.github.sarwaruddin.aviary.model.BirdSummary
import com.github.sarwaruddin.aviary.model.Sighting
import com.github.sarwaruddin.aviary.ui.common.OrnamentDivider
import com.github.sarwaruddin.aviary.ui.theme.Field
import kotlin.coroutines.cancellation.CancellationException

private sealed interface Load {
    data object Loading : Load
    data object NotFound : Load
    data class Ready(val bird: Bird) : Load
}

private val TopBarHeight = 56.dp

/** A species page, set like a plate from a well-made printed field guide. */
@Composable
fun BirdScreen(id: String, onBack: () -> Unit, onOpenBird: (String) -> Unit, onLogSighting: () -> Unit) {
    // key(id): a fresh page (state, scroll, pager) for each bird pushed on the stack.
    key(id) {
        val state by produceState<Load>(Load.Loading) {
            val b = try {
                AppGraph.birds.bird(id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
            value = if (b != null) Load.Ready(b) else Load.NotFound
        }
        BoxWithConstraints(Modifier.fillMaxSize().background(Field.colors.paper)) {
            val heroHeight = (maxHeight * 0.53f).coerceAtLeast(240.dp)
            when (val s = state) {
                Load.Loading -> LoadingPage(heroHeight, onBack)
                Load.NotFound -> NotFoundPage(onBack)
                is Load.Ready -> BirdPage(s.bird, heroHeight, onBack, onOpenBird, onLogSighting)
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// The page

@Composable
private fun BirdPage(
    bird: Bird,
    heroHeight: Dp,
    onBack: () -> Unit,
    onOpenBird: (String) -> Unit,
    onLogSighting: () -> Unit,
) {
    var viewerIndex by remember { mutableStateOf<Int?>(null) }
    val list = rememberLazyListState()
    val pager = rememberPagerState(pageCount = { bird.photos.size.coerceAtLeast(1) })
    val sightings by remember(bird.id) { AppGraph.sightings.sightingsOf(bird.id) }
        .collectAsState<List<Sighting>, List<Sighting>?>(initial = null)
    val family by produceState(emptyList<BirdSummary>(), bird.family.id, bird.id) {
        value = try {
            AppGraph.birds.birds(BirdFilter(familyId = bird.family.id, includeRare = true))
                .filter { it.id != bird.id }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emptyList()
        }
    }
    val relatives = remember(family) { family.take(12) }
    val s = bird.summary
    val hasAbout = !bird.summaryEn.isNullOrBlank() || !bird.summaryBn.isNullOrBlank()

    // The slim bar appears once the hero's bottom passes under it.
    val density = LocalDensity.current
    val statusPx = WindowInsets.statusBars.getTop(density)
    val threshold = with(density) { (heroHeight - TopBarHeight).toPx() } - statusPx
    val collapsed by remember(threshold) {
        derivedStateOf { list.firstVisibleItemIndex > 0 || list.firstVisibleItemScrollOffset > threshold }
    }
    val barAlpha by animateFloatAsState(if (collapsed) 1f else 0f, tween(220), label = "topBar")

    Box(Modifier.fillMaxSize()) {
        LazyColumn(state = list, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
            item(key = "hero") {
                // Hero and names in one item, so the names draw over the hero's fade.
                Column {
                    Hero(bird, pager, list, heroHeight, onOpenPhoto = { viewerIndex = it })
                    NamesBlock(
                        bird,
                        credit = bird.photos.getOrNull(pager.currentPage)?.credit,
                        modifier = Modifier.pullUp(NameOverlap),
                    )
                }
            }
            item(key = "glance") { AtAGlance(bird) }
            if (bird.facts.isNotEmpty()) item(key = "facts") { FieldNotes(bird.facts) }
            if (hasAbout) item(key = "about") { About(bird) }
            item(key = "sightings") { SightingsBlock(sightings, onLogSighting) }
            if (relatives.isNotEmpty()) item(key = "family") { Relatives(relatives, family.size, onOpenBird) }
            if (bird.ebirdCode != null || bird.wikiEn != null) item(key = "links") { Links(bird) }
            item(key = "end") { OrnamentDivider(Modifier.padding(top = 28.dp)) }
        }

        if (barAlpha < 1f) {
            FloatingBack(
                onBack,
                Modifier
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(start = 12.dp, top = 8.dp)
                    .graphicsLayer { alpha = 1f - barAlpha },
            )
        }
        if (barAlpha > 0f) {
            CollapsedBar(s.nameEn, onBack, Modifier.graphicsLayer { alpha = barAlpha })
        }
    }

    viewerIndex?.let { i ->
        bird.photos.getOrNull(i)?.let { p ->
            PhotoViewer(p, s.nameEn, onDismiss = { viewerIndex = null })
        }
    }
}

/** Slim paper top bar shown once the hero has scrolled away. */
@Composable
private fun CollapsedBar(title: String, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val f = Field.colors
    Column(modifier.fillMaxWidth().background(f.paper)) {
        Row(
            Modifier
                .windowInsetsPadding(WindowInsets.statusBars)
                .fillMaxWidth()
                .height(TopBarHeight)
                .padding(start = 4.dp, end = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = f.ink)
            }
            Spacer(Modifier.width(4.dp))
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                color = f.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        HorizontalDivider(thickness = 1.dp, color = f.rule)
    }
}

// ---------------------------------------------------------------------------------------------
// States

@Composable
private fun LoadingPage(heroHeight: Dp, onBack: () -> Unit) {
    val f = Field.colors
    val pulse by rememberInfiniteTransition(label = "skeleton").animateFloat(
        initialValue = 0.45f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "skeletonPulse",
    )
    val bar = f.rule.copy(alpha = 0.6f)
    val shape = RoundedCornerShape(6.dp)
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxWidth().height(heroHeight).background(f.card)) {
                HeroFade(Modifier.align(Alignment.BottomCenter).fillMaxWidth().fillMaxHeight(0.42f))
            }
            Column(
                Modifier
                    .pullUp(NameOverlap)
                    .fillMaxWidth()
                    .padding(horizontal = PageGutter)
                    .graphicsLayer { alpha = pulse },
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(Modifier.width(52.dp).height(10.dp).background(bar, shape))
                Box(Modifier.fillMaxWidth(0.72f).height(30.dp).background(bar, shape))
                Box(Modifier.fillMaxWidth(0.4f).height(24.dp).background(bar, shape))
                Box(Modifier.fillMaxWidth(0.5f).height(14.dp).background(bar, shape))
                Spacer(Modifier.height(18.dp))
                repeat(2) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(Modifier.weight(1f).height(72.dp).background(bar, RoundedCornerShape(12.dp)))
                        Box(Modifier.weight(1f).height(72.dp).background(bar, RoundedCornerShape(12.dp)))
                    }
                }
            }
        }
        FloatingBack(
            onBack,
            Modifier.windowInsetsPadding(WindowInsets.statusBars).padding(start = 12.dp, top = 8.dp),
        )
    }
}

@Composable
private fun NotFoundPage(onBack: () -> Unit) {
    val f = Field.colors
    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier.align(Alignment.Center).padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Not in this book", style = MaterialTheme.typography.headlineSmall, color = f.ink)
            Spacer(Modifier.height(8.dp))
            Text(
                "This bird could not be found in the guide.",
                style = MaterialTheme.typography.bodyLarge,
                color = f.inkSoft,
                textAlign = TextAlign.Center,
            )
            OrnamentDivider(Modifier.padding(vertical = 8.dp))
            TextButton(onClick = onBack) { Text("Go back", color = f.moss) }
        }
        FloatingBack(
            onBack,
            Modifier.windowInsetsPadding(WindowInsets.statusBars).padding(start = 12.dp, top = 8.dp),
        )
    }
}
