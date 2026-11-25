package com.github.sarwaruddin.aviary.ui.log

import android.text.format.DateFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.github.sarwaruddin.aviary.data.AppGraph
import com.github.sarwaruddin.aviary.model.BirdFilter
import com.github.sarwaruddin.aviary.model.BirdSummary
import com.github.sarwaruddin.aviary.model.Sighting
import com.github.sarwaruddin.aviary.ui.theme.Field
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset

/** Log a new sighting. [speciesId] null = pick a species first. */
@Composable
fun AddSightingSheet(speciesId: String?, onDismiss: () -> Unit, onSaved: () -> Unit) {
    SightingEditorSheet(initial = null, speciesId = speciesId, onDismiss = onDismiss, onSaved = onSaved)
}

/**
 * Shared editor. [initial] non-null = edit that sighting (its species wins over [speciesId]);
 * saving then calls update() instead of add().
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SightingEditorSheet(
    initial: Sighting?,
    speciesId: String?,
    onDismiss: () -> Unit,
    onSaved: () -> Unit,
) {
    val f = Field.colors
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val startSpecies = initial?.speciesId ?: speciesId

    // ---- form state ----
    var bird by remember { mutableStateOf<BirdSummary?>(null) }
    var picking by rememberSaveable { mutableStateOf(startSpecies == null) }
    var count by rememberSaveable { mutableIntStateOf(initial?.count ?: 1) }
    var whenMillis by rememberSaveable { mutableLongStateOf(initial?.timeMillis ?: System.currentTimeMillis()) }
    var notes by rememberSaveable { mutableStateOf(initial?.notes ?: "") }
    var place by rememberSaveable { mutableStateOf(initial?.place ?: "") }
    var useLocation by rememberSaveable { mutableStateOf(initial?.lat != null && initial?.lon != null) }
    var lat by rememberSaveable { mutableStateOf(initial?.lat) }
    var lon by rememberSaveable { mutableStateOf(initial?.lon) }
    var locating by remember { mutableStateOf(false) }
    var locationMsg by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    var saveError by remember { mutableStateOf<String?>(null) }
    var showDate by remember { mutableStateOf(false) }
    var showTime by remember { mutableStateOf(false) }
    val is24 = remember { DateFormat.is24HourFormat(context) }

    LaunchedEffect(startSpecies) {
        if (startSpecies != null && bird == null) {
            bird = runCatching { AppGraph.birds.bird(startSpecies)?.summary }.getOrNull()
            if (bird == null && initial == null) picking = true
        }
    }

    fun fetchLocation() {
        locating = true
        locationMsg = null
        scope.launch {
            val loc = runCatching { currentFix(context) }.getOrNull()
            locating = false
            if (loc != null) {
                lat = loc.latitude
                lon = loc.longitude
            } else if (lat == null) {
                locationMsg = "Couldn't get a fix. Is location switched on? You can type the place instead."
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        if (result.values.any { it }) {
            fetchLocation()
        } else {
            useLocation = false
            locationMsg = "Location permission denied. No matter, just write the place below."
        }
    }

    fun setUseLocation(on: Boolean) {
        useLocation = on
        locationMsg = null
        if (on) {
            if (hasLocationPermission(context)) fetchLocation() else permissionLauncher.launch(LocationPermissions)
        } else {
            lat = null
            lon = null
            locating = false
        }
    }

    fun save() {
        val b = bird ?: return
        saving = true
        saveError = null
        val s = Sighting(
            id = initial?.id ?: 0,
            speciesId = b.id,
            timeMillis = whenMillis,
            lat = if (useLocation) lat else null,
            lon = if (useLocation) lon else null,
            place = place.trim().ifEmpty { null },
            count = count,
            notes = notes.trim(),
        )
        scope.launch {
            val ok = runCatching {
                if (initial == null) AppGraph.sightings.add(s) else AppGraph.sightings.update(s)
            }.isSuccess
            if (ok) {
                runCatching { sheetState.hide() }
                onSaved()
            } else {
                saving = false
                saveError = "Couldn't save that entry. Try again?"
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = f.card,
        contentColor = f.ink,
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
            Text(
                if (initial == null) "Log a sighting" else "Edit sighting",
                style = MaterialTheme.typography.headlineSmall, color = f.ink,
            )
            Text(
                "পাখি দেখার খাতা",
                style = MaterialTheme.typography.bodyMedium, color = f.inkSoft,
            )
            Spacer(Modifier.height(12.dp))

            if (picking) {
                SpeciesPicker(
                    onPick = { bird = it; picking = false },
                    onCancel = if (bird != null) ({ picking = false }) else null,
                )
                Spacer(Modifier.height(16.dp))
                return@Column
            }

            Column(Modifier.verticalScroll(rememberScrollState())) {
                // ---- species ----
                RuledCard(Modifier.fillMaxWidth(), margin = false) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Thumb(bird, 56.dp)
                        Spacer(Modifier.width(12.dp))
                        if (bird == null) {
                            Text("Finding the bird…", color = f.inkSoft, modifier = Modifier.weight(1f))
                        } else {
                            BirdNames(bird, startSpecies ?: "", Modifier.weight(1f))
                        }
                        TextButton(onClick = { picking = true }) { Text("Change") }
                    }
                }

                SectionLabel("How many")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedIconButton(onClick = { if (count > 1) count-- }, enabled = count > 1) {
                        Text("−", style = MaterialTheme.typography.titleLarge)
                    }
                    Text(
                        count.toString(),
                        style = MaterialTheme.typography.headlineSmall,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.widthIn(min = 64.dp),
                    )
                    OutlinedIconButton(onClick = { if (count < 9999) count++ }) {
                        Icon(Icons.Filled.Add, contentDescription = "One more")
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(if (count == 1) "bird" else "birds", color = f.inkSoft)
                }

                SectionLabel("When")
                val dt = whenMillis.toLocalDateTime()
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AssistChip(
                        onClick = { showDate = true },
                        label = { Text(dt.format(DayFormat)) },
                        leadingIcon = { Icon(Icons.Filled.DateRange, null, Modifier.size(AssistChipDefaults.IconSize)) },
                    )
                    AssistChip(
                        onClick = { showTime = true },
                        label = { Text(timeText(dt, is24)) },
                        trailingIcon = { Icon(Icons.Filled.KeyboardArrowDown, null, Modifier.size(AssistChipDefaults.IconSize)) },
                    )
                }

                SectionLabel("Where")
                OutlinedTextField(
                    value = place,
                    onValueChange = { place = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("e.g. Ramna Park, Dhaka") },
                    leadingIcon = { Icon(Icons.Filled.Place, contentDescription = null) },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    colors = fieldColors(),
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable(enabled = !locating) { setUseLocation(!useLocation) }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.LocationOn, contentDescription = null, tint = f.moss)
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Use my location", style = MaterialTheme.typography.bodyMedium)
                        val la = lat
                        val lo = lon
                        when {
                            locating -> Text("Locating…", style = MaterialTheme.typography.bodySmall, color = f.inkSoft)
                            useLocation && la != null && lo != null -> Text(
                                coordsText(la, lo),
                                style = MaterialTheme.typography.bodySmall, color = f.moss,
                            )
                        }
                    }
                    if (locating) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = f.moss)
                        Spacer(Modifier.width(12.dp))
                    }
                    Switch(
                        checked = useLocation,
                        onCheckedChange = { setUseLocation(it) },
                        enabled = !locating,
                        colors = SwitchDefaults.colors(checkedTrackColor = f.moss, checkedThumbColor = f.paper),
                    )
                }
                locationMsg?.let { msg ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            msg, style = MaterialTheme.typography.bodySmall, color = f.rust,
                            modifier = Modifier.weight(1f),
                        )
                        if (useLocation && !locating) TextButton(onClick = { fetchLocation() }) { Text("Retry") }
                    }
                }

                SectionLabel("Notes")
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    placeholder = { Text("Behaviour, call, plumage, weather…") },
                    textStyle = MaterialTheme.typography.bodyLarge,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    colors = fieldColors(),
                )

                saveError?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, color = f.rust, style = MaterialTheme.typography.bodySmall)
                }

                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = { save() },
                        enabled = bird != null && !saving,
                        colors = ButtonDefaults.buttonColors(containerColor = f.moss, contentColor = f.paper),
                    ) { Text(if (initial == null) "Save to log" else "Save changes") }
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }

    if (showDate) {
        val dt = whenMillis.toLocalDateTime()
        val todayEndUtc = LocalDate.now().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val dateState = rememberDatePickerState(
            initialSelectedDateMillis = dt.toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis <= todayEndUtc
                override fun isSelectableYear(year: Int) = year <= LocalDate.now().year
            },
        )
        DatePickerDialog(
            onDismissRequest = { showDate = false },
            confirmButton = {
                TextButton(onClick = {
                    dateState.selectedDateMillis?.let { utc ->
                        val day = Instant.ofEpochMilli(utc).atZone(ZoneOffset.UTC).toLocalDate()
                        whenMillis = LocalDateTime.of(day, dt.toLocalTime()).toMillis()
                    }
                    showDate = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showDate = false }) { Text("Cancel") } },
        ) {
            DatePicker(state = dateState)
        }
    }

    if (showTime) {
        val dt = whenMillis.toLocalDateTime()
        val timeState = rememberTimePickerState(initialHour = dt.hour, initialMinute = dt.minute, is24Hour = is24)
        AlertDialog(
            onDismissRequest = { showTime = false },
            confirmButton = {
                TextButton(onClick = {
                    whenMillis = LocalDateTime.of(dt.toLocalDate(), LocalTime.of(timeState.hour, timeState.minute)).toMillis()
                    showTime = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showTime = false }) { Text("Cancel") } },
            title = { Text("Time seen") },
            text = { Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { TimePicker(state = timeState) } },
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Spacer(Modifier.height(16.dp))
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = Field.colors.inkSoft,
    )
    Spacer(Modifier.height(6.dp))
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Field.colors.moss,
    unfocusedBorderColor = Field.colors.rule,
    focusedContainerColor = Field.colors.paper,
    unfocusedContainerColor = Field.colors.paper,
    cursorColor = Field.colors.moss,
)

/** Search over all species (rare ones included). */
@Composable
private fun SpeciesPicker(onPick: (BirdSummary) -> Unit, onCancel: (() -> Unit)?) {
    val f = Field.colors
    var query by rememberSaveable { mutableStateOf("") }
    var results by remember { mutableStateOf<List<BirdSummary>?>(null) }

    LaunchedEffect(query) {
        if (query.isNotEmpty()) delay(200)   // debounce typing
        results = runCatching {
            AppGraph.birds.birds(BirdFilter(query = query.trim(), includeRare = true))
        }.getOrDefault(emptyList())
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Which bird?", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        if (onCancel != null) TextButton(onClick = onCancel) { Text("Keep current") }
    }
    Spacer(Modifier.height(8.dp))
    OutlinedTextField(
        value = query,
        onValueChange = { query = it },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        placeholder = { Text("doyel, দোয়েল, Copsychus…") },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) IconButton(onClick = { query = "" }) {
                Icon(Icons.Filled.Close, contentDescription = "Clear search")
            }
        },
        colors = fieldColors(),
    )
    Spacer(Modifier.height(8.dp))

    val list = results
    when {
        list == null -> Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = f.moss)
        }
        list.isEmpty() -> Text(
            "No bird by that name in the guide. Try English, Bangla or the scientific name.",
            style = MaterialTheme.typography.bodyLarge.copy(fontStyle = FontStyle.Italic),
            color = f.inkSoft,
            modifier = Modifier.padding(vertical = 24.dp),
        )
        else -> LazyColumn(Modifier.fillMaxWidth().heightIn(max = 440.dp)) {
            items(list, key = { it.id }) { b ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onPick(b) }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Thumb(b, 44.dp)
                    Spacer(Modifier.width(12.dp))
                    BirdNames(b, b.id, Modifier.weight(1f))
                }
                HorizontalDivider(color = f.rule.copy(alpha = 0.6f))
            }
        }
    }
}
