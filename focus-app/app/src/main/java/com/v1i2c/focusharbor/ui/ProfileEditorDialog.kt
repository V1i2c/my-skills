package com.v1i2c.focusharbor.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.v1i2c.focusharbor.domain.DomainMatcher
import com.v1i2c.focusharbor.domain.FocusProfile
import com.v1i2c.focusharbor.domain.GeofenceRule
import com.v1i2c.focusharbor.domain.QuotaPeriod
import com.v1i2c.focusharbor.domain.ScheduleRule
import com.v1i2c.focusharbor.domain.SubAppFeature
import com.v1i2c.focusharbor.domain.UsageQuota
import com.v1i2c.focusharbor.platform.LaunchableApp
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
internal fun ProfileEditorDialog(
    profile: FocusProfile,
    installedApps: List<LaunchableApp>,
    onDismiss: () -> Unit,
    onSave: (FocusProfile, String?) -> Unit,
) {
    var name by rememberSaveable(profile.id) { mutableStateOf(profile.name) }
    var selectedApps by remember(profile.id) { mutableStateOf(profile.appPackages) }
    var websiteText by rememberSaveable(profile.id) { mutableStateOf(profile.websiteDomains.sorted().joinToString("\n")) }
    var allowList by rememberSaveable(profile.id) { mutableStateOf(profile.allowList) }
    var blockMessage by rememberSaveable(profile.id) { mutableStateOf(profile.blockMessage) }
    var blockNotifications by rememberSaveable(profile.id) { mutableStateOf(profile.blockNotifications) }
    var autoBlockNewApps by rememberSaveable(profile.id) { mutableStateOf(profile.autoBlockNewApps) }
    var blockUnsupportedBrowsers by rememberSaveable(profile.id) { mutableStateOf(profile.blockUnsupportedBrowsers) }
    var wifiText by rememberSaveable(profile.id) { mutableStateOf(profile.wifiSsids.sorted().joinToString("\n")) }
    var appPickerOpen by rememberSaveable { mutableStateOf(false) }
    var quotaPickerOpen by rememberSaveable { mutableStateOf(false) }
    var validation by remember { mutableStateOf<String?>(null) }

    val currentSchedule = profile.schedules.firstOrNull()
    var scheduleEnabled by rememberSaveable(profile.id) { mutableStateOf(profile.schedules.any { it.enabled }) }
    var scheduleDays by remember(profile.id) { mutableStateOf(currentSchedule?.daysOfWeek ?: setOf(1, 2, 3, 4, 5)) }
    var startTime by rememberSaveable(profile.id) { mutableStateOf(currentSchedule?.let { formatMinute(it.startMinute) } ?: "09:00") }
    var endTime by rememberSaveable(profile.id) { mutableStateOf(currentSchedule?.let { formatMinute(it.endMinute) } ?: "17:00") }

    var quotaTarget by rememberSaveable(profile.id) { mutableStateOf(profile.quotas.keys.firstOrNull().orEmpty()) }
    val selectedQuota = profile.quotas[quotaTarget]
    var quotaMinutes by remember(profile.id, quotaTarget) { mutableStateOf(selectedQuota?.maximumMinutes?.toString().orEmpty()) }
    var quotaLaunches by remember(profile.id, quotaTarget) { mutableStateOf(selectedQuota?.maximumLaunches?.toString().orEmpty()) }
    var quotaPeriod by remember(profile.id, quotaTarget) { mutableStateOf(selectedQuota?.period ?: QuotaPeriod.DAILY) }
    var quotaMenuOpen by rememberSaveable { mutableStateOf(false) }

    var useGeofence by rememberSaveable(profile.id) { mutableStateOf(profile.geofence != null) }
    var latitude by rememberSaveable(profile.id) { mutableStateOf(profile.geofence?.latitude?.toString().orEmpty()) }
    var longitude by rememberSaveable(profile.id) { mutableStateOf(profile.geofence?.longitude?.toString().orEmpty()) }
    var radius by rememberSaveable(profile.id) { mutableStateOf(profile.geofence?.radiusMeters?.toInt()?.toString() ?: "250") }

    val originalStrict = profile.strictConfig
    var strictEnabled by rememberSaveable(profile.id) { mutableStateOf(originalStrict.enabled) }
    var lockEditing by rememberSaveable(profile.id) { mutableStateOf(originalStrict.lockEditing) }
    var timerText by rememberSaveable(profile.id) { mutableStateOf(originalStrict.timerMinutes?.toString().orEmpty()) }
    var requireScheduleEnd by rememberSaveable(profile.id) { mutableStateOf(originalStrict.requireScheduleEnd) }
    var requireCharger by rememberSaveable(profile.id) { mutableStateOf(originalStrict.requireCharger) }
    var requirePin by rememberSaveable(profile.id) { mutableStateOf(originalStrict.requirePin) }
    var newPin by rememberSaveable(profile.id) { mutableStateOf("") }
    var cooldownText by rememberSaveable(profile.id) { mutableStateOf(originalStrict.cooldownMinutes.toString()) }
    var emergencyCooldownText by rememberSaveable(profile.id) { mutableStateOf(originalStrict.emergencyCooldownMinutes.toString()) }
    var subFeatures by remember(profile.id) { mutableStateOf(profile.subAppFeatures) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.97f).fillMaxHeight(0.94f),
            shape = RoundedCornerShape(27.dp),
            color = MaterialTheme.colorScheme.background,
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(if (profile.name == "New focus profile") "Create a profile" else "Edit profile", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Text("Rules stay local to this device.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    }
                    TextButton(onClick = onDismiss) { Text("Close") }
                }
                Column(
                    Modifier.weight(1f).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    EditorSection("Profile") {
                        OutlinedTextField(
                            value = name,
                            onValueChange = { if (it.length <= 80) name = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Profile name") },
                            singleLine = true,
                        )
                        OutlinedButton(onClick = { appPickerOpen = true }, modifier = Modifier.fillMaxWidth()) {
                            Text("Choose apps · ${selectedApps.size} selected")
                        }
                        OutlinedTextField(
                            value = websiteText,
                            onValueChange = { websiteText = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Website domains · one per line") },
                            supportingText = { Text("Subdomains match automatically. Example: reddit.com") },
                            minLines = 2,
                            maxLines = 5,
                        )
                        EditorToggle(
                            title = "Allow only selected apps and sites",
                            detail = "When enabled, unlisted apps and websites are blocked while this profile is active.",
                            checked = allowList,
                            onCheckedChange = { allowList = it },
                        )
                        OutlinedTextField(
                            value = blockMessage,
                            onValueChange = { if (it.length <= 240) blockMessage = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Pause-screen message") },
                            minLines = 2,
                            maxLines = 3,
                        )
                        EditorToggle("Block notifications for selected apps", "Requires optional Notification access in Settings.", blockNotifications) { blockNotifications = it }
                        EditorToggle("Automatically block newly installed apps", "In allowlist mode, new apps stay unlisted and are blocked.", autoBlockNewApps) { autoBlockNewApps = it }
                        EditorToggle("Block unsupported known browsers", "Best effort: blocks listed browser apps when their address bar cannot be checked.", blockUnsupportedBrowsers) { blockUnsupportedBrowsers = it }
                    }

                    EditorSection("Schedule") {
                        EditorToggle("Use a weekly schedule", "No schedule means the profile can run whenever it is enabled.", scheduleEnabled) { scheduleEnabled = it }
                        if (scheduleEnabled) {
                            Text("Days of week", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                listOf("M", "T", "W", "T", "F", "S", "S").forEachIndexed { index, label ->
                                    val day = index + 1
                                    FilterChip(
                                        selected = day in scheduleDays,
                                        onClick = { scheduleDays = if (day in scheduleDays) scheduleDays - day else scheduleDays + day },
                                        label = { Text(label) },
                                    )
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = startTime,
                                    onValueChange = { if (it.length <= 5) startTime = it },
                                    modifier = Modifier.weight(1f),
                                    label = { Text("Start · HH:mm") },
                                    singleLine = true,
                                )
                                OutlinedTextField(
                                    value = endTime,
                                    onValueChange = { if (it.length <= 5) endTime = it },
                                    modifier = Modifier.weight(1f),
                                    label = { Text("End · HH:mm") },
                                    singleLine = true,
                                )
                            }
                            Text("Overnight ranges are supported; the end time is exclusive.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                        }
                    }

                    EditorSection("Usage quotas") {
                        Text("Set a time and/or launch limit for one app. Choose another app to add another quota.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                        OutlinedButton(onClick = { quotaPickerOpen = true }, modifier = Modifier.fillMaxWidth()) {
                            val label = installedApps.firstOrNull { it.packageName == quotaTarget }?.label ?: quotaTarget.ifBlank { "Choose an app" }
                            Text(if (quotaTarget.isBlank()) "Choose quota app" else "Quota app · $label", maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = quotaMinutes,
                                onValueChange = { if (it.length <= 7 && it.all(Char::isDigit)) quotaMinutes = it },
                                modifier = Modifier.weight(1f),
                                label = { Text("Minutes") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                            )
                            OutlinedTextField(
                                value = quotaLaunches,
                                onValueChange = { if (it.length <= 7 && it.all(Char::isDigit)) quotaLaunches = it },
                                modifier = Modifier.weight(1f),
                                label = { Text("Launches") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Period", modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Box {
                                TextButton(onClick = { quotaMenuOpen = true }) { Text(quotaPeriod.name.lowercase().replaceFirstChar(Char::uppercase)) }
                                DropdownMenu(expanded = quotaMenuOpen, onDismissRequest = { quotaMenuOpen = false }) {
                                    QuotaPeriod.entries.forEach { period ->
                                        DropdownMenuItem(
                                            text = { Text(period.name.lowercase().replaceFirstChar(Char::uppercase)) },
                                            onClick = { quotaPeriod = period; quotaMenuOpen = false },
                                        )
                                    }
                                }
                            }
                        }
                        if (profile.quotas.isNotEmpty()) {
                            Text("Existing quotas: ${profile.quotas.keys.joinToString()}", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                        }
                    }

                    EditorSection("Device triggers") {
                        OutlinedTextField(
                            value = wifiText,
                            onValueChange = { wifiText = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Wi-Fi names · one per line") },
                            supportingText = { Text("Leave blank for any network. Android may hide the current network name.") },
                            minLines = 1,
                            maxLines = 3,
                        )
                        EditorToggle("Use a geofence", "Uses the last known location when permission is available.", useGeofence) { useGeofence = it }
                        if (useGeofence) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = latitude,
                                    onValueChange = { latitude = it },
                                    modifier = Modifier.weight(1f),
                                    label = { Text("Latitude") },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                )
                                OutlinedTextField(
                                    value = longitude,
                                    onValueChange = { longitude = it },
                                    modifier = Modifier.weight(1f),
                                    label = { Text("Longitude") },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                )
                            }
                            OutlinedTextField(
                                value = radius,
                                onValueChange = { if (it.length <= 7 && it.all(Char::isDigit)) radius = it },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("Radius in metres · 50–100000") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                            )
                        }
                    }

                    EditorSection("Sub-app sections · best effort") {
                        Text("Some app versions expose accessibility view IDs that hint at a section. This can change without notice.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                        SubAppFeature.entries.forEach { feature ->
                            EditorToggle(featureLabel(feature), "", feature in subFeatures) {
                                subFeatures = if (it) subFeatures + feature else subFeatures - feature
                            }
                        }
                    }

                    EditorSection("Strict Mode") {
                        EditorToggle("Enable Strict Mode for this profile", "Adds a deliberate release gate; it cannot prevent Android Settings or uninstall controls.", strictEnabled) { strictEnabled = it }
                        if (strictEnabled) {
                            EditorToggle("Lock profile edits while active", "Emergency release remains available after its reflection and cooldown.", lockEditing) { lockEditing = it }
                            OutlinedTextField(
                                value = timerText,
                                onValueChange = { if (it.length <= 7 && it.all(Char::isDigit)) timerText = it },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("Minimum timer in minutes · blank for none") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                            )
                            EditorToggle("Require schedule to end", "Normal release waits until a configured schedule is inactive.", requireScheduleEnd) { requireScheduleEnd = it }
                            EditorToggle("Require charging power", "Normal release checks Android's current power connection state.", requireCharger) { requireCharger = it }
                            EditorToggle("Require a PIN", "PIN is salted and hashed locally; it is never stored in plain text.", requirePin) { requirePin = it }
                            if (requirePin) {
                                OutlinedTextField(
                                    value = newPin,
                                    onValueChange = { if (it.length <= 12 && it.all(Char::isDigit)) newPin = it },
                                    modifier = Modifier.fillMaxWidth(),
                                    label = { Text(if (originalStrict.pinVerifier == null) "Set PIN · 4–12 digits" else "Change PIN · leave blank to keep current") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                                    singleLine = true,
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = cooldownText,
                                    onValueChange = { if (it.length <= 7 && it.all(Char::isDigit)) cooldownText = it },
                                    modifier = Modifier.weight(1f),
                                    label = { Text("Release cooldown · min") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true,
                                )
                                OutlinedTextField(
                                    value = emergencyCooldownText,
                                    onValueChange = { if (it.length <= 4 && it.all(Char::isDigit)) emergencyCooldownText = it },
                                    modifier = Modifier.weight(1f),
                                    label = { Text("Emergency cooldown") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true,
                                )
                            }
                            Text("Emergency unlock requires three reflection answers, an exact typed commitment and a cooldown (1–1440 minutes).", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }

                validation?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Button(onClick = {
                        validation = null
                        val cleanName = name.trim()
                        if (cleanName.isEmpty()) {
                            validation = "Give this profile a name."
                            return@Button
                        }
                        val domainValues = websiteText.lines().map(String::trim).filter(String::isNotEmpty)
                        val normalizedDomains = domainValues.mapNotNull(DomainMatcher::normalize)
                        if (normalizedDomains.size != domainValues.size) {
                            validation = "Check the website hostnames and try again."
                            return@Button
                        }
                        val schedules = if (scheduleEnabled) {
                            val start = parseMinute(startTime)
                            val end = parseMinute(endTime)
                            if (start == null || end == null || start == end || scheduleDays.isEmpty()) {
                                validation = "Enter valid, different schedule times and select at least one day."
                                return@Button
                            }
                            listOf(ScheduleRule(scheduleDays, start, end))
                        } else emptyList()
                        val maxTime = quotaMinutes.toLongOrNull()
                        val maxLaunch = quotaLaunches.toIntOrNull()
                        if (quotaMinutes.isNotBlank() && (maxTime == null || maxTime <= 0L)) {
                            validation = "Quota minutes must be a positive number."
                            return@Button
                        }
                        if (quotaLaunches.isNotBlank() && (maxLaunch == null || maxLaunch <= 0)) {
                            validation = "Quota launches must be a positive number."
                            return@Button
                        }
                        if (quotaTarget.isBlank() && (maxTime != null || maxLaunch != null)) {
                            validation = "Choose an app for this quota."
                            return@Button
                        }
                        if (requirePin && originalStrict.pinVerifier == null && newPin.length !in 4..12) {
                            validation = "Set a 4–12 digit PIN before requiring PIN confirmation."
                            return@Button
                        }
                        if (newPin.isNotBlank() && newPin.length !in 4..12) {
                            validation = "PINs must contain 4–12 digits."
                            return@Button
                        }
                        val timer = timerText.toLongOrNull()
                        if (timerText.isNotBlank() && (timer == null || timer !in 1L..(365L * 24L * 60L))) {
                            validation = "Strict Mode timer must be between 1 minute and 1 year."
                            return@Button
                        }
                        val cooldown = cooldownText.toLongOrNull()
                        if (cooldown == null || cooldown !in 0L..(365L * 24L * 60L)) {
                            validation = "Release cooldown must be between zero minutes and one year."
                            return@Button
                        }
                        val emergencyCooldown = emergencyCooldownText.toLongOrNull()
                        if (emergencyCooldown == null || emergencyCooldown !in 1L..1440L) {
                            validation = "Emergency cooldown must be between 1 and 1440 minutes."
                            return@Button
                        }
                        val geofence = if (useGeofence) {
                            val lat = latitude.toDoubleOrNull()
                            val lon = longitude.toDoubleOrNull()
                            val meters = radius.toFloatOrNull()
                            if (lat == null || lon == null || meters == null || lat !in -90.0..90.0 || lon !in -180.0..180.0 || meters !in 50f..100_000f) {
                                validation = "Enter valid coordinates and a radius from 50 to 100000 metres."
                                return@Button
                            }
                            GeofenceRule(lat, lon, meters)
                        } else null
                        val quotas = profile.quotas.toMutableMap()
                        if (quotaTarget.isNotBlank()) {
                            if (maxTime == null && maxLaunch == null) quotas.remove(quotaTarget)
                            else quotas[quotaTarget] = UsageQuota(maximumMinutes = maxTime, maximumLaunches = maxLaunch, period = quotaPeriod)
                        }
                        val strictConfig = profile.strictConfig.copy(
                            enabled = strictEnabled,
                            lockEditing = lockEditing,
                            timerMinutes = timer,
                            requireScheduleEnd = requireScheduleEnd,
                            requireCharger = requireCharger,
                            requirePin = requirePin,
                            cooldownMinutes = cooldown,
                            emergencyCooldownMinutes = emergencyCooldown,
                        )
                        val updated = profile.copy(
                            name = cleanName,
                            appPackages = selectedApps,
                            websiteDomains = normalizedDomains.toSet(),
                            schedules = schedules,
                            quotas = quotas,
                            allowList = allowList,
                            blockMessage = blockMessage.trim().ifBlank { "This app is paused for your focus." },
                            blockNotifications = blockNotifications,
                            autoBlockNewApps = autoBlockNewApps,
                            blockUnsupportedBrowsers = blockUnsupportedBrowsers,
                            wifiSsids = wifiText.lines().map(String::trim).filter(String::isNotEmpty).toSet(),
                            geofence = geofence,
                            subAppFeatures = subFeatures,
                            strictConfig = strictConfig,
                        )
                        onSave(updated, newPin.takeIf(String::isNotBlank))
                    }) { Text("Save profile") }
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                }
            }
        }
    }

    if (appPickerOpen) {
        AppPickerDialog(
            title = "Profile apps",
            detail = "Choose launchable apps to block, or to keep in an allowlist profile.",
            apps = installedApps,
            initialSelection = selectedApps,
            onDismiss = { appPickerOpen = false },
            onSave = { selectedApps = it; appPickerOpen = false },
        )
    }
    if (quotaPickerOpen) {
        AppPickerDialog(
            title = "Choose quota app",
            detail = "Only one app is selected for this quota editor. Existing quotas for other apps are preserved.",
            apps = installedApps,
            initialSelection = setOf(quotaTarget).filter(String::isNotBlank).toSet(),
            singleSelection = true,
            onDismiss = { quotaPickerOpen = false },
            onSave = {
                quotaTarget = it.firstOrNull().orEmpty()
                val quota = profile.quotas[quotaTarget]
                quotaMinutes = quota?.maximumMinutes?.toString().orEmpty()
                quotaLaunches = quota?.maximumLaunches?.toString().orEmpty()
                quotaPeriod = quota?.period ?: QuotaPeriod.DAILY
                quotaPickerOpen = false
            },
        )
    }
}

@Composable
internal fun AppPickerDialog(
    title: String,
    detail: String,
    apps: List<LaunchableApp>,
    initialSelection: Set<String>,
    singleSelection: Boolean = false,
    onDismiss: () -> Unit,
    onSave: (Set<String>) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var selected by remember(initialSelection) { mutableStateOf(initialSelection) }
    var showSystemApps by remember(initialSelection, apps) {
        mutableStateOf(initialSelection.any { packageName -> apps.any { it.packageName == packageName && it.isSystemApp } })
    }
    val filtered = remember(apps, query, showSystemApps) {
        apps.filter { app ->
            (showSystemApps || !app.isSystemApp) &&
                (query.isBlank() || app.label.contains(query, ignoreCase = true) || app.packageName.contains(query, ignoreCase = true))
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(detail, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(value = query, onValueChange = { query = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Search apps") }, singleLine = true)
                if (apps.any(LaunchableApp::isSystemApp)) {
                    FilterChip(
                        selected = showSystemApps,
                        onClick = { showSystemApps = !showSystemApps },
                        label = { Text("Show system apps") },
                    )
                }
                if (apps.isEmpty()) {
                    Text("No launchable apps were found.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    LazyColumn(Modifier.fillMaxWidth().heightIn(max = 390.dp)) {
                        items(filtered, key = LaunchableApp::packageName) { app ->
                            Row(
                                Modifier.fillMaxWidth().clickable {
                                    selected = if (app.packageName in selected) selected - app.packageName
                                    else if (singleSelection) setOf(app.packageName) else selected + app.packageName
                                }.padding(vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(app.label, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
                                    Text(app.packageName, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Checkbox(checked = app.packageName in selected, onCheckedChange = { checked ->
                                    selected = when {
                                        checked && singleSelection -> setOf(app.packageName)
                                        checked -> selected + app.packageName
                                        else -> selected - app.packageName
                                    }
                                })
                            }
                        }
                    }
                }
                Text("${selected.size} selected", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
            }
        },
        confirmButton = { Button(onClick = { onSave(selected) }) { Text("Use selection") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun EditorSection(title: String, content: @Composable () -> Unit) {
    Card(shape = RoundedCornerShape(19.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            content()
        }
    }
}

@Composable
private fun EditorToggle(title: String, detail: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 8.dp)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            if (detail.isNotBlank()) Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

private fun parseMinute(value: String): Int? = runCatching {
    LocalTime.parse(value.trim(), DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT)).let { it.hour * 60 + it.minute }
}.getOrNull()

private fun formatMinute(minute: Int): String = String.format(Locale.ROOT, "%02d:%02d", minute / 60, minute % 60)

private fun featureLabel(feature: SubAppFeature): String = when (feature) {
    SubAppFeature.YOUTUBE_SHORTS -> "YouTube Shorts"
    SubAppFeature.INSTAGRAM_REELS -> "Instagram Reels"
    SubAppFeature.INSTAGRAM_STORIES -> "Instagram Stories"
    SubAppFeature.SNAPCHAT_STORIES -> "Snapchat Stories"
    SubAppFeature.SNAPCHAT_SPOTLIGHT -> "Snapchat Spotlight"
    SubAppFeature.WHATSAPP_STATUS -> "WhatsApp Status"
    SubAppFeature.WHATSAPP_CHANNELS -> "WhatsApp Channels"
}
