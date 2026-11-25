package com.github.sarwaruddin.aviary.ui.about

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.github.sarwaruddin.aviary.data.AppGraph
import com.github.sarwaruddin.aviary.ui.common.OrnamentDivider
import com.github.sarwaruddin.aviary.ui.common.SectionHeader
import com.github.sarwaruddin.aviary.ui.theme.Field

/** About Aviary + data credits. Wikipedia text is CC BY-SA, so attribution must be reachable in-app. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutSheet(onDismiss: () -> Unit) {
    val f = Field.colors
    val meta by produceState<Map<String, String>>(emptyMap()) {
        value = runCatching { AppGraph.birds.meta() }.getOrDefault(emptyMap())
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = f.paper,
    ) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp).navigationBarsPadding().padding(bottom = 24.dp),
        ) {
            Text("Aviary", style = MaterialTheme.typography.headlineMedium, color = f.ink)
            Text("A field guide to the birds of Bangladesh · বাংলাদেশের পাখি",
                style = MaterialTheme.typography.bodyLarge, color = f.inkSoft)
            Spacer(Modifier.height(8.dp))
            val count = meta["species_count"]
            val families = meta["family_count"]
            if (count != null) Text(
                "$count species in ${families ?: "?"} families. Browsing, names, notes and your log work offline; " +
                    "full-size photos load when you're online and are kept for later.",
                style = MaterialTheme.typography.bodyMedium, color = f.ink,
            )

            Spacer(Modifier.height(20.dp))
            SectionHeader("Credits")
            Spacer(Modifier.height(8.dp))
            Credit("Species list", "Wikipedia, \"List of birds of Bangladesh\" (Clements taxonomy), CC BY-SA 4.0",
                "https://en.wikipedia.org/wiki/List_of_birds_of_Bangladesh")
            Credit("Descriptions", "Wikipedia contributors (English and বাংলা), CC BY-SA 4.0. Each bird page links its article.",
                "https://creativecommons.org/licenses/by-sa/4.0/")
            Credit("Names, status, links", "Wikidata, CC0", "https://www.wikidata.org")
            Credit("Photos", "Wikimedia Commons photographers. The author and licence are shown under every photo, which links to its source.",
                "https://commons.wikimedia.org")
            Credit("Seasons and field notes", "Curated for Aviary from the sources above; corrections welcome.", null)

            Spacer(Modifier.height(20.dp))
            SectionHeader("This data")
            Spacer(Modifier.height(8.dp))
            Text(
                listOfNotNull(
                    meta["data_version"]?.let { "Data version $it" },
                    meta["built_at"]?.let { "built ${it.take(10)}" },
                ).joinToString(" · ").ifEmpty { "—" },
                style = MaterialTheme.typography.bodySmall, color = f.inkSoft,
            )
            OrnamentDivider(Modifier.padding(top = 16.dp))
        }
    }
}

@Composable
private fun Credit(title: String, text: String, url: String?) {
    val f = Field.colors
    val ctx = LocalContext.current
    Column(
        Modifier.fillMaxWidth()
            .then(if (url != null) Modifier.clickable {
                runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
            } else Modifier)
            .padding(vertical = 8.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = f.ink)
        Text(text, style = MaterialTheme.typography.bodyMedium, color = f.inkSoft)
        if (url != null) Text(url.removePrefix("https://"), style = MaterialTheme.typography.bodySmall,
            color = f.moss, textDecoration = TextDecoration.Underline)
    }
}
