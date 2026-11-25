package com.github.sarwaruddin.aviary

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.graphics.toArgb
import com.github.sarwaruddin.aviary.ui.theme.DarkField
import com.github.sarwaruddin.aviary.ui.theme.LightField
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.github.sarwaruddin.aviary.data.AppGraph
import com.github.sarwaruddin.aviary.ui.browse.BrowseScreen
import com.github.sarwaruddin.aviary.ui.detail.BirdScreen
import com.github.sarwaruddin.aviary.ui.log.AddSightingSheet
import com.github.sarwaruddin.aviary.ui.log.LogScreen
import com.github.sarwaruddin.aviary.ui.month.MonthScreen
import com.github.sarwaruddin.aviary.ui.theme.AviaryTheme
import com.github.sarwaruddin.aviary.ui.theme.Field

enum class Tab(val label: String) { BIRDS("Birds"), MONTH("This month"), LOG("My log") }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Paper-coloured system bars (no white scrim strip on 3-button navigation).
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(LightField.paper.toArgb(), DarkField.paper.toArgb()),
        )
        // Android 10+ lays a translucent contrast scrim over 3-button navigation; our bar is already paper.
        if (android.os.Build.VERSION.SDK_INT >= 29) window.isNavigationBarContrastEnforced = false
        AppGraph.init(this)
        setContent { AviaryTheme { AviaryApp() } }
    }
}

/**
 * Navigation: three tabs, plus a stack of bird pages on top.
 * The "log a sighting" sheet can be opened from anywhere, optionally pre-filled with a species.
 */
@Composable
fun AviaryApp() {
    var tab by rememberSaveable { mutableStateOf(Tab.BIRDS) }
    val stack = remember { mutableStateListOf<String>() }      // bird ids
    var logFor by remember { mutableStateOf<String?>(null) }   // species id, "" = pick a species
    val openBird: (String) -> Unit = { stack.add(it) }
    val logSighting: (String?) -> Unit = { logFor = it ?: "" }

    BackHandler(enabled = stack.isNotEmpty()) { stack.removeAt(stack.lastIndex) }

    val f = Field.colors
    Scaffold(
        containerColor = f.paper,
        // Screens handle the status bar themselves: tabs get statusBarsPadding below,
        // the bird page draws its hero photo under it.
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            if (stack.isEmpty()) NavigationBar(containerColor = f.card) {
                Tab.entries.forEach { t ->
                    NavigationBarItem(
                        selected = tab == t,
                        onClick = { tab = t },
                        icon = {
                            Icon(when (t) {
                                Tab.BIRDS -> Icons.Filled.Search
                                Tab.MONTH -> Icons.Filled.DateRange
                                Tab.LOG -> Icons.Filled.Edit
                            }, contentDescription = null)
                        },
                        label = { Text(t.label) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = f.moss, selectedTextColor = f.moss,
                            unselectedIconColor = f.inkSoft, unselectedTextColor = f.inkSoft,
                            indicatorColor = f.fern.copy(alpha = 0.22f),
                        ),
                    )
                }
            }
        },
    ) { pad ->
        Box(Modifier.padding(bottom = pad.calculateBottomPadding())) {
            val top = stack.lastOrNull()
            if (top != null) Box(Modifier.navigationBarsPadding()) {
                BirdScreen(
                    id = top,
                    onBack = { stack.removeAt(stack.lastIndex) },
                    onOpenBird = openBird,
                    onLogSighting = { logSighting(top) },
                )
            } else Box(Modifier.statusBarsPadding()) { when (tab) {
                Tab.BIRDS -> BrowseScreen(onOpenBird = openBird)
                Tab.MONTH -> MonthScreen(onOpenBird = openBird)
                Tab.LOG -> LogScreen(onOpenBird = openBird, onLogSighting = { logSighting(null) })
            } }
        }
    }

    logFor?.let { sp ->
        AddSightingSheet(
            speciesId = sp.ifEmpty { null },
            onDismiss = { logFor = null },
            onSaved = { logFor = null },
        )
    }
}

/** Screens are standalone composables with these exact signatures:
 *  ui/browse:  BrowseScreen(onOpenBird: (String) -> Unit)
 *  ui/month:   MonthScreen(onOpenBird: (String) -> Unit)
 *  ui/log:     LogScreen(onOpenBird: (String) -> Unit, onLogSighting: () -> Unit)
 *              AddSightingSheet(speciesId: String?, onDismiss: () -> Unit, onSaved: () -> Unit)
 *  ui/detail:  BirdScreen(id: String, onBack: () -> Unit, onOpenBird: (String) -> Unit, onLogSighting: () -> Unit)
 */
@Suppress("unused") private val screenContracts = Unit
