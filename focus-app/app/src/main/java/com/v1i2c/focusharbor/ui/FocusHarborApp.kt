package com.v1i2c.focusharbor.ui

import android.content.Context
import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.v1i2c.focusharbor.domain.DomainMatcher
import com.v1i2c.focusharbor.domain.FocusAppState
import com.v1i2c.focusharbor.domain.FocusProfile
import com.v1i2c.focusharbor.domain.QuotaPeriod
import com.v1i2c.focusharbor.platform.AppCatalog
import com.v1i2c.focusharbor.platform.LaunchableApp
import com.v1i2c.focusharbor.platform.PermissionState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val HarborInk = Color(0xFF14373C)
private val HarborTeal = Color(0xFF167D72)
private val HarborMint = Color(0xFFD8EFE9)
private val HarborCanvas = Color(0xFFF3F7F5)
private val HarborMuted = Color(0xFF63777A)
private val HarborGold = Color(0xFFF3C66A)

@Composable
fun FocusHarborTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = androidx.compose.material3.lightColorScheme(
            primary = HarborTeal,
            onPrimary = Color.White,
            secondary = Color(0xFFB9694F),
            background = HarborCanvas,
            surface = Color.White,
            onSurface = HarborInk,
            onBackground = HarborInk,
            surfaceVariant = Color(0xFFE5EFEC),
            onSurfaceVariant = HarborMuted,
            error = Color(0xFFB33F32),
        ),
        content = content,
    )
}

private data class HarborTab(val route: String, val label: String, val icon: ImageVector)

private val harborTabs = listOf(
    HarborTab("home", "Home", Icons.Filled.Home),
    HarborTab("profiles", "Profiles", Icons.Filled.Tune),
    HarborTab("websites", "Websites", Icons.Filled.Language),
    HarborTab("insights", "Insights", Icons.Filled.BarChart),
    HarborTab("settings", "Settings", Icons.Filled.Settings),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FocusHarborApp(
    viewModel: FocusViewModel,
    permissionRevision: Int,
    onOpenAccessibilitySettings: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onRequestLocationPermissions: () -> Unit,
    onExportBackup: () -> Unit,
    onImportBackup: () -> Unit,
    onExportWebsiteCsv: () -> Unit,
    onImportWebsiteCsv: () -> Unit,
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var selectedRoute by rememberSaveable { mutableStateOf("home") }
    var editProfile by remember { mutableStateOf<FocusProfile?>(null) }
    var deleteProfile by remember { mutableStateOf<FocusProfile?>(null) }
    var quickTargetsOpen by rememberSaveable { mutableStateOf(false) }
    var releaseProfile by remember { mutableStateOf<FocusProfile?>(null) }
    var emergencyProfile by remember { mutableStateOf<FocusProfile?>(null) }

    val installedApps by produceState(initialValue = emptyList(), key1 = context) {
        value = withContext(Dispatchers.IO) {
            runCatching { AppCatalog.launchableApps(context, includeSystemApps = true) }.getOrDefault(emptyList())
        }
    }

    LaunchedEffect(message) {
        val current = message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(current)
        viewModel.dismissMessage()
    }

    Scaffold(
        containerColor = HarborCanvas,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("FOCUS HARBOR", style = MaterialTheme.typography.labelMedium, letterSpacing = 1.6.sp, fontWeight = FontWeight.Bold)
                        Text(
                            when (selectedRoute) {
                                "profiles" -> "Your focus profiles"
                                "websites" -> "Apps & websites"
                                "insights" -> "Your patterns"
                                "settings" -> "Private by design"
                                else -> "Make room for what matters"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = HarborMuted,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { selectedRoute = "settings" }) {
                        Icon(Icons.Filled.Shield, contentDescription = "Open privacy and permissions")
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = HarborCanvas),
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            NavigationBar(containerColor = Color.White, tonalElevation = 0.dp) {
                harborTabs.forEach { tab ->
                    NavigationBarItem(
                        selected = selectedRoute == tab.route,
                        onClick = { selectedRoute = tab.route },
                        icon = { Icon(tab.icon, contentDescription = null) },
                        label = { Text(tab.label, maxLines = 1) },
                    )
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (selectedRoute) {
                "profiles" -> ProfilesScreen(
                    state = state,
                    onCreate = { editProfile = FocusProfile(name = "New focus profile") },
                    onEdit = { editProfile = it },
                    onDelete = { deleteProfile = it },
                    onDuplicate = viewModel::duplicateProfile,
                    onToggle = viewModel::toggleProfile,
                    onPause = viewModel::pauseProfile,
                    onResume = viewModel::resumeProfile,
                    onStartStrict = viewModel::startStrictMode,
                    onExtendStrict = viewModel::extendStrictTimer,
                    onRelease = { releaseProfile = it },
                    onEmergency = { emergencyProfile = it },
                )
                "websites" -> WebsitesScreen(
                    state = state,
                    onSettingsChange = { transform -> viewModel.updateSettings(transform) },
                    onImportCsv = onImportWebsiteCsv,
                    onExportCsv = onExportWebsiteCsv,
                    onEditProfile = { editProfile = it },
                )
                "insights" -> InsightsScreen(state)
                "settings" -> SettingsScreen(
                    state = state,
                    context = context,
                    permissionRevision = permissionRevision,
                    installedApps = installedApps,
                    onOpenAccessibilitySettings = onOpenAccessibilitySettings,
                    onOpenNotificationSettings = onOpenNotificationSettings,
                    onOpenAppSettings = onOpenAppSettings,
                    onRequestLocationPermissions = onRequestLocationPermissions,
                    onSettingsChange = { transform -> viewModel.updateSettings(transform) },
                    onExportBackup = onExportBackup,
                    onImportBackup = onImportBackup,
                    onClearNotifications = viewModel::clearBlockedNotifications,
                )
                else -> HomeScreen(
                    state = state,
                    context = context,
                    permissionRevision = permissionRevision,
                    onOpenProfiles = { selectedRoute = "profiles" },
                    onOpenSettings = { selectedRoute = "settings" },
                    onOpenWebsites = { selectedRoute = "websites" },
                    onOpenQuickTargets = { quickTargetsOpen = true },
                    onStartQuickBlock = viewModel::startQuickBlock,
                    onStopQuickBlock = viewModel::stopQuickBlock,
                    onOpenAccessibilitySettings = onOpenAccessibilitySettings,
                )
            }
        }
    }

    editProfile?.let { profile ->
        ProfileEditorDialog(
            profile = profile,
            installedApps = installedApps,
            onDismiss = { editProfile = null },
            onSave = { updated, pin ->
                viewModel.saveProfile(updated, pin)
                editProfile = null
            },
        )
    }

    deleteProfile?.let { profile ->
        AlertDialog(
            onDismissRequest = { deleteProfile = null },
            title = { Text("Delete ${profile.name}?") },
            text = { Text("This removes the profile and its local rules. The change cannot be undone unless you have an exported backup.") },
            confirmButton = {
                Button(onClick = {
                    viewModel.deleteProfile(profile.id)
                    deleteProfile = null
                }) { Text("Delete profile") }
            },
            dismissButton = { TextButton(onClick = { deleteProfile = null }) { Text("Keep it") } },
        )
    }

    if (quickTargetsOpen) {
        QuickTargetsDialog(
            state = state,
            installedApps = installedApps,
            onDismiss = { quickTargetsOpen = false },
            onSave = { apps, domains, minutes ->
                viewModel.updateSettings { current ->
                    current.copy(settings = current.settings.copy(
                        quickBlockApps = apps,
                        quickBlockDomains = domains,
                        quickBlockMinutes = minutes,
                    ))
                }
                quickTargetsOpen = false
            },
        )
    }

    releaseProfile?.let { selected ->
        val profile = state.profiles.firstOrNull { it.id == selected.id } ?: selected
        StrictReleaseDialog(
            profile = profile,
            onDismiss = { releaseProfile = null },
            onRelease = { pin ->
                viewModel.releaseStrictMode(profile.id, pin)
                releaseProfile = null
            },
        )
    }

    emergencyProfile?.let { selected ->
        val profile = state.profiles.firstOrNull { it.id == selected.id } ?: selected
        EmergencyUnlockDialog(
            profile = profile,
            onDismiss = { emergencyProfile = null },
            onBegin = { viewModel.beginEmergencyUnlock(profile.id) },
            onComplete = { reflection, commitment ->
                viewModel.completeEmergencyUnlock(profile.id, commitment, reflection)
                emergencyProfile = null
            },
        )
    }
}

@Composable
private fun HomeScreen(
    state: FocusAppState,
    context: Context,
    permissionRevision: Int,
    onOpenProfiles: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenWebsites: () -> Unit,
    onOpenQuickTargets: () -> Unit,
    onStartQuickBlock: (Long, Set<String>, Set<String>) -> Unit,
    onStopQuickBlock: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
) {
    val enabledCount = state.profiles.count { it.enabled || it.strictSession != null }
    val accessibilityOn = remember(context, permissionRevision) { PermissionState.accessibilityEnabled(context) }
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        HeroCard(enabledCount = enabledCount)
        QuickBlockCard(
            state = state,
            onConfigure = onOpenQuickTargets,
            onStart = onStartQuickBlock,
            onStop = onStopQuickBlock,
        )
        if (!accessibilityOn) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF2D4)),
                shape = RoundedCornerShape(22.dp),
            ) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("One permission unlocks app blocking", fontWeight = FontWeight.Bold, color = HarborInk)
                    Text(
                        "Turn on Focus Harbor in Android Accessibility settings. It checks the foreground app and supported browser address bars locally.",
                        color = HarborMuted,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Button(onClick = onOpenAccessibilitySettings) { Text("Open Accessibility settings") }
                }
            }
        }
        SectionHeading("Your setup", "A few small steps make Focus Harbor useful.")
        ActionCard(
            icon = Icons.Filled.Tune,
            title = "Build a profile",
            detail = "Choose apps, websites, schedules, quotas and Strict Mode rules.",
            actionLabel = if (state.profiles.isEmpty()) "Create a profile" else "View profiles",
            onClick = onOpenProfiles,
        )
        ActionCard(
            icon = Icons.Filled.Language,
            title = "Shape your web boundaries",
            detail = "Manage local website lists and optional adult-domain blocking.",
            actionLabel = "Manage websites",
            onClick = onOpenWebsites,
        )
        ActionCard(
            icon = Icons.Filled.Settings,
            title = "Permissions & privacy",
            detail = "Optional notification capture, local backups and device triggers.",
            actionLabel = "Review settings",
            onClick = onOpenSettings,
        )
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun HeroCard(enabledCount: Int) {
    Card(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = HarborInk),
    ) {
        Box(
            Modifier.fillMaxWidth().background(
                Brush.linearGradient(listOf(Color(0xFF183F45), Color(0xFF236B65), Color(0xFF2D8577))),
            ),
        ) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Surface(color = Color.White.copy(alpha = 0.12f), shape = CircleShape) {
                    Text("YOUR DAY, YOUR ATTENTION", Modifier.padding(horizontal = 12.dp, vertical = 7.dp), color = Color.White, style = MaterialTheme.typography.labelSmall, letterSpacing = 1.2.sp)
                }
                Text(
                    "A little more\nroom to focus.",
                    color = Color.White,
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.SemiBold,
                    lineHeight = 39.sp,
                )
                Text(
                    "Profiles, schedules and quick sessions stay on this device.",
                    color = Color(0xFFD7EAE6),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Shield, contentDescription = null, tint = HarborGold, modifier = Modifier.size(18.dp))
                    Text("$enabledCount profile${if (enabledCount == 1) "" else "s"} enabled", color = Color.White, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

@Composable
private fun QuickBlockCard(
    state: FocusAppState,
    onConfigure: () -> Unit,
    onStart: (Long, Set<String>, Set<String>) -> Unit,
    onStop: () -> Unit,
) {
    var selectedMinutes by rememberSaveable { mutableStateOf(state.settings.quickBlockMinutes.coerceIn(1L, 365L * 24L * 60L)) }
    var elapsedNow by remember { mutableStateOf(SystemClock.elapsedRealtime()) }
    val session = state.quickBlock
    LaunchedEffect(session) {
        while (true) {
            elapsedNow = SystemClock.elapsedRealtime()
            delay(1_000L)
        }
    }
    val nowEpoch = System.currentTimeMillis()
    val active = session != null && !session.isExpired(nowEpoch, elapsedNow)
    val remaining = session?.remainingMillis(nowEpoch, elapsedNow) ?: 0L
    val targetCount = state.settings.quickBlockApps.size + state.settings.quickBlockDomains.size

    Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.fillMaxWidth().padding(19.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(color = HarborMint, shape = CircleShape, modifier = Modifier.size(42.dp)) {
                    Box(contentAlignment = Alignment.Center) { Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = HarborTeal) }
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Quick Block", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("A one-tap pause for chosen apps and sites", style = MaterialTheme.typography.bodySmall, color = HarborMuted)
                }
                TextButton(onClick = onConfigure) { Text("Targets") }
            }
            if (active && session != null) {
                Text(formatClock(remaining), style = MaterialTheme.typography.headlineMedium, color = HarborTeal, fontWeight = FontWeight.Bold)
                LinearProgressIndicator(
                    progress = (remaining.toFloat() / session.durationMillis.toFloat()).coerceIn(0f, 1f),
                    modifier = Modifier.fillMaxWidth().height(7.dp).clip(CircleShape),
                    color = HarborTeal,
                    trackColor = HarborMint,
                )
                Text("${session.appPackages.size} apps · ${session.websiteDomains.size} sites · ends automatically", color = HarborMuted, style = MaterialTheme.typography.bodySmall)
                Button(onClick = onStop, modifier = Modifier.fillMaxWidth()) { Text("End Quick Block") }
            } else {
                Text("Choose a short session", fontWeight = FontWeight.SemiBold, color = HarborInk)
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    listOf(15L, 25L, 45L, 60L).forEach { minutes ->
                        FilterChip(
                            selected = selectedMinutes == minutes,
                            onClick = { selectedMinutes = minutes },
                            label = { Text("${minutes}m") },
                        )
                    }
                }
                if (targetCount == 0) {
                    Text("Add at least one app or website to begin.", color = HarborMuted, style = MaterialTheme.typography.bodySmall)
                } else {
                    Text("$targetCount targets ready", color = HarborMuted, style = MaterialTheme.typography.bodySmall)
                }
                Button(
                    onClick = { onStart(selectedMinutes, state.settings.quickBlockApps, state.settings.quickBlockDomains) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = targetCount > 0,
                ) { Text("Start ${selectedMinutes}-minute Quick Block") }
            }
        }
    }
}

@Composable
private fun ProfilesScreen(
    state: FocusAppState,
    onCreate: () -> Unit,
    onEdit: (FocusProfile) -> Unit,
    onDelete: (FocusProfile) -> Unit,
    onDuplicate: (String) -> Unit,
    onToggle: (String, Boolean) -> Unit,
    onPause: (String, Long) -> Unit,
    onResume: (String) -> Unit,
    onStartStrict: (String) -> Unit,
    onExtendStrict: (String, Long) -> Unit,
    onRelease: (FocusProfile) -> Unit,
    onEmergency: (FocusProfile) -> Unit,
) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Profiles", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("Mix schedules, app lists and website rules.", color = HarborMuted)
            }
            Button(onClick = onCreate) { Text("New profile") }
        }
        if (state.profiles.isEmpty()) {
            EmptyCard(
                title = "Start with one simple boundary",
                detail = "Create a profile, choose a few distracting apps or websites, then decide when it should run.",
            )
        } else {
            state.profiles.forEach { profile ->
                ProfileCard(
                    profile = profile,
                    onEdit = { onEdit(profile) },
                    onDelete = { onDelete(profile) },
                    onDuplicate = { onDuplicate(profile.id) },
                    onToggle = { onToggle(profile.id, it) },
                    onPause = { onPause(profile.id, it) },
                    onResume = { onResume(profile.id) },
                    onStartStrict = { onStartStrict(profile.id) },
                    onExtendStrict = { onExtendStrict(profile.id, 25L) },
                    onRelease = { onRelease(profile) },
                    onEmergency = { onEmergency(profile) },
                )
            }
        }
        NoteCard("Strict Mode is a commitment aid, not a device-management lock. Android Settings, force-stop, uninstall and some OEM/browser behavior remain outside the app's control.")
    }
}

@Composable
private fun ProfileCard(
    profile: FocusProfile,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onDuplicate: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onPause: (Long) -> Unit,
    onResume: () -> Unit,
    onStartStrict: () -> Unit,
    onExtendStrict: () -> Unit,
    onRelease: () -> Unit,
    onEmergency: () -> Unit,
) {
    val strict = profile.strictSession != null
    val paused = profile.pauseUntilEpochMillis?.let { System.currentTimeMillis() < it } == true
    Card(shape = RoundedCornerShape(23.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.fillMaxWidth().padding(17.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(profile.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        when {
                            strict -> "Strict Mode is active"
                            paused -> "Paused until ${DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault()).format(java.time.Instant.ofEpochMilli(profile.pauseUntilEpochMillis!!).atZone(java.time.ZoneId.systemDefault()))}"
                            profile.enabled -> "Profile enabled"
                            else -> "Profile off"
                        },
                        color = if (strict) HarborTeal else HarborMuted,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Switch(checked = profile.enabled || strict, onCheckedChange = onToggle, enabled = !strict)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                StatPill("${profile.appPackages.size} apps")
                StatPill("${profile.websiteDomains.size} sites")
                if (profile.schedules.isNotEmpty()) StatPill("Scheduled")
                if (profile.quotas.isNotEmpty()) StatPill("${profile.quotas.size} quotas")
            }
            if (profile.blockMessage.isNotBlank()) {
                Text(profile.blockMessage, color = HarborMuted, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                OutlinedButton(onClick = onEdit, enabled = !strict || !profile.strictConfig.lockEditing) { Text("Edit") }
                OutlinedButton(onClick = onDuplicate, enabled = !strict) { Text("Copy") }
                OutlinedButton(onClick = onDelete, enabled = !strict) { Text("Delete") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                if (strict) {
                    Button(onClick = onRelease) { Text("Release") }
                    OutlinedButton(onClick = onEmergency) { Text("Emergency unlock") }
                    if (profile.strictSession?.timerEndsAtEpochMillis != null) {
                        TextButton(onClick = onExtendStrict) { Text("+25 min") }
                    }
                } else {
                    if (profile.strictConfig.enabled) {
                        Button(onClick = onStartStrict) { Icon(Icons.Filled.Lock, contentDescription = null, modifier = Modifier.size(17.dp)); Spacer(Modifier.width(5.dp)); Text("Start Strict") }
                    }
                    if (profile.enabled && !paused) {
                        TextButton(onClick = { onPause(15L) }) { Text("Pause 15 min") }
                    } else if (paused) {
                        TextButton(onClick = onResume) { Text("Resume") }
                    }
                }
            }
        }
    }
}

@Composable
private fun WebsitesScreen(
    state: FocusAppState,
    onSettingsChange: ((FocusAppState) -> FocusAppState) -> Unit,
    onImportCsv: () -> Unit,
    onExportCsv: () -> Unit,
    onEditProfile: (FocusProfile) -> Unit,
) {
    var newDomain by rememberSaveable { mutableStateOf("") }
    var validation by remember { mutableStateOf<String?>(null) }
    var localDomainText by remember(state.settings.adultDomains) {
        mutableStateOf(state.settings.adultDomains.sorted().joinToString("\n"))
    }
    val context = LocalContext.current
    val accessibilityOn = remember(context, state.settings.adultContentBlockingEnabled) { PermissionState.accessibilityEnabled(context) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SectionHeading("Websites", "Keep website rules local and understandable.")
        Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Column(Modifier.padding(17.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Adult-domain blocking", fontWeight = FontWeight.Bold)
                        Text("Uses only the domains you store here; no remote list is downloaded.", color = HarborMuted, style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(
                        checked = state.settings.adultContentBlockingEnabled,
                        onCheckedChange = { enabled -> onSettingsChange { it.copy(settings = it.settings.copy(adultContentBlockingEnabled = enabled)) } },
                    )
                }
                androidx.compose.material3.OutlinedTextField(
                    value = localDomainText,
                    onValueChange = { localDomainText = it },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 110.dp),
                    label = { Text("Local domains, one per line") },
                    supportingText = { Text("Subdomains are included. Use a hostname such as example.com.") },
                    minLines = 3,
                    maxLines = 6,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        val parsed = localDomainText.lines().map(String::trim).filter(String::isNotEmpty)
                        val normalized = parsed.mapNotNull(DomainMatcher::normalize)
                        if (normalized.size != parsed.size) validation = "One or more entries are not valid hostnames."
                        else {
                            validation = null
                            onSettingsChange { it.copy(settings = it.settings.copy(adultDomains = normalized.toSet())) }
                        }
                    }) { Text("Save list") }
                    OutlinedButton(onClick = onImportCsv) { Text("Import CSV") }
                    OutlinedButton(onClick = onExportCsv) { Text("Export CSV") }
                }
                validation?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                if (state.settings.adultDomains.isNotEmpty()) {
                    Text("${state.settings.adultDomains.size} domains stored on this device", color = HarborMuted, style = MaterialTheme.typography.labelMedium)
                }
                if (state.settings.adultContentBlockingEnabled && !accessibilityOn) {
                    Text("Turn on the Accessibility Service in Settings for best-effort browser checks.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        SectionHeading("Profile website rules", "Tap a profile to edit its block or allowlist.")
        val withDomains = state.profiles.filter { it.websiteDomains.isNotEmpty() }
        if (withDomains.isEmpty()) {
            EmptyCard("No profile websites yet", "Add domains while editing a profile. A rule can block a site or allow only selected sites.")
        } else {
            withDomains.forEach { profile ->
                Card(
                    modifier = Modifier.fillMaxWidth().clickable { onEditProfile(profile) },
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(profile.name, modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold)
                            Text(if (profile.allowList) "Allowlist" else "Blocklist", color = HarborTeal, style = MaterialTheme.typography.labelMedium)
                        }
                        Text(profile.websiteDomains.sorted().joinToString(" · "), color = HarborMuted, maxLines = 3, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        NoteCard("Website detection reads the address-bar text exposed by supported browsers. Browser updates, private tabs, embedded webviews, VPNs, and OEM changes can limit coverage; this app does not install a VPN or alter DNS.")
    }
}

@Composable
private fun InsightsScreen(state: FocusAppState) {
    val todayKey = LocalDate.now().toString()
    val todayStats = state.dailyStats.firstOrNull { it.dateKey == todayKey }
    val dailyUsage = state.usage.filter { it.periodKey == todayKey && it.period == QuotaPeriod.DAILY }
    val totalMinutes = dailyUsage.sumOf { it.foregroundMillis } / 60_000L
    val topApps = dailyUsage.sortedByDescending { it.foregroundMillis }.take(5)
    val week = state.dailyStats.sortedBy { it.dateKey }.takeLast(7)
    val maxMinutes = week.maxOfOrNull { it.focusMinutes }?.coerceAtLeast(1L) ?: 1L

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SectionHeading("Insights", "Small observations, stored only on your device.")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MetricCard("Tracked today", formatMinutes(totalMinutes), Modifier.weight(1f))
            MetricCard("Screen unlocks", (todayStats?.screenUnlocks ?: 0).toString(), Modifier.weight(1f))
        }
        Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Column(Modifier.fillMaxWidth().padding(17.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Focus time · last 7 days", fontWeight = FontWeight.Bold)
                if (week.isEmpty()) {
                    Text("Once an enabled profile or Quick Block session runs, your local chart will begin to fill in.", color = HarborMuted, style = MaterialTheme.typography.bodySmall)
                } else {
                    Row(
                        Modifier.fillMaxWidth().height(142.dp),
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        verticalAlignment = Alignment.Bottom,
                    ) {
                        week.forEach { day ->
                            val fraction = (day.focusMinutes.toFloat() / maxMinutes.toFloat()).coerceIn(0f, 1f)
                            Column(
                                Modifier.weight(1f),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Bottom,
                            ) {
                                Text("${day.focusMinutes}m", style = MaterialTheme.typography.labelSmall, color = HarborMuted, maxLines = 1)
                                Box(
                                    Modifier.width(22.dp)
                                        .height((12f + 68f * fraction).dp)
                                        .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                                        .background(if (day.dateKey == todayKey) HarborTeal else HarborMint),
                                )
                                Text(runCatching { LocalDate.parse(day.dateKey).dayOfWeek.name.take(2) }.getOrDefault("—"), style = MaterialTheme.typography.labelSmall, color = HarborMuted)
                            }
                        }
                    }
                }
            }
        }
        Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Column(Modifier.fillMaxWidth().padding(17.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Top tracked apps today", fontWeight = FontWeight.Bold)
                if (topApps.isEmpty()) {
                    Text("No tracked app time yet.", color = HarborMuted, style = MaterialTheme.typography.bodySmall)
                } else {
                    topApps.forEach { record ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(record.packageName, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                                Text("${record.launches} launches · ${record.blockedAttempts} blocked attempts", color = HarborMuted, style = MaterialTheme.typography.labelSmall)
                            }
                            Text(formatMinutes(record.foregroundMillis / 60_000L), color = HarborTeal, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
        NoteCard("Usage is approximate and depends on the Accessibility Service being enabled. Focus time counts while a profile or Quick Block session is active; Android may pause or restrict background services on some devices.")
    }
}

@Composable
private fun SettingsScreen(
    state: FocusAppState,
    context: Context,
    permissionRevision: Int,
    installedApps: List<LaunchableApp>,
    onOpenAccessibilitySettings: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onRequestLocationPermissions: () -> Unit,
    onSettingsChange: ((FocusAppState) -> FocusAppState) -> Unit,
    onExportBackup: () -> Unit,
    onImportBackup: () -> Unit,
    onClearNotifications: () -> Unit,
) {
    val accessibilityEnabled = remember(context, permissionRevision) { PermissionState.accessibilityEnabled(context) }
    val notificationEnabled = remember(context, permissionRevision) { PermissionState.notificationListenerEnabled(context) }
    val fineLocation = remember(context, permissionRevision) {
        androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED ||
            androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_COARSE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }
    var ignoredPickerOpen by rememberSaveable { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SectionHeading("Settings", "Everything works offline; permissions are optional and explained.")
        Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Column(Modifier.padding(17.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
                Text("Android permissions", fontWeight = FontWeight.Bold)
                PermissionRow(
                    title = "Accessibility service",
                    detail = if (accessibilityEnabled) "Enabled · app and supported browser checks are available" else "Needed for foreground app blocking and local usage estimates",
                    enabled = accessibilityEnabled,
                    action = onOpenAccessibilitySettings,
                    buttonText = if (accessibilityEnabled) "Review" else "Enable",
                )
                PermissionRow(
                    title = "Notification access",
                    detail = if (notificationEnabled) "Enabled · blocked alerts can be held locally" else "Optional · used only if you turn notification blocking on",
                    enabled = notificationEnabled,
                    action = onOpenNotificationSettings,
                    buttonText = if (notificationEnabled) "Review" else "Enable",
                )
                PermissionRow(
                    title = "Location / Wi-Fi triggers",
                    detail = if (fineLocation) "Location permission granted" else "Optional · needed to evaluate a saved geofence or read a Wi-Fi name",
                    enabled = fineLocation,
                    action = onRequestLocationPermissions,
                    buttonText = if (fineLocation) "Update" else "Grant",
                )
                TextButton(onClick = onOpenAppSettings) { Text("Open Android app settings") }
            }
        }

        Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Column(Modifier.padding(17.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Optional local features", fontWeight = FontWeight.Bold)
                SettingToggle(
                    title = "Capture blocked notifications",
                    detail = "Requires Notification access. Matching alerts are cancelled and kept on this device for review.",
                    checked = state.settings.notificationBlockingEnabled,
                    onCheckedChange = { value -> onSettingsChange { it.copy(settings = it.settings.copy(notificationBlockingEnabled = value)) } },
                )
                SettingToggle(
                    title = "Adult-domain list",
                    detail = "Checks only the hostnames in your own local website list; there is no downloaded category feed.",
                    checked = state.settings.adultContentBlockingEnabled,
                    onCheckedChange = { value -> onSettingsChange { it.copy(settings = it.settings.copy(adultContentBlockingEnabled = value)) } },
                )
                SettingToggle(
                    title = "Wi-Fi profile triggers",
                    detail = "Evaluate saved network names when Android exposes them.",
                    checked = state.settings.wifiTriggersEnabled,
                    onCheckedChange = { value -> onSettingsChange { it.copy(settings = it.settings.copy(wifiTriggersEnabled = value)) } },
                )
                SettingToggle(
                    title = "Location profile triggers",
                    detail = "Use the last known device location for a saved geofence; updates are not continuously requested.",
                    checked = state.settings.locationTriggersEnabled,
                    onCheckedChange = { value -> onSettingsChange { it.copy(settings = it.settings.copy(locationTriggersEnabled = value)) } },
                )
                OutlinedButton(onClick = { ignoredPickerOpen = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("Ignore apps in statistics (${state.settings.ignoredStatisticsPackages.size})")
                }
            }
        }

        Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Column(Modifier.padding(17.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Local backup", fontWeight = FontWeight.Bold)
                Text("Export or restore your profiles and settings as a file you choose. Nothing is uploaded.", color = HarborMuted, style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onExportBackup) { Text("Export JSON") }
                    OutlinedButton(onClick = onImportBackup) { Text("Import JSON") }
                }
            }
        }

        Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Column(Modifier.padding(17.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Blocked notifications", fontWeight = FontWeight.Bold)
                        Text("${state.blockedNotifications.size} saved locally", color = HarborMuted, style = MaterialTheme.typography.bodySmall)
                    }
                    if (state.blockedNotifications.isNotEmpty()) TextButton(onClick = onClearNotifications) { Text("Clear") }
                }
                if (state.blockedNotifications.isEmpty()) {
                    Text("No blocked alerts have been captured.", color = HarborMuted, style = MaterialTheme.typography.bodySmall)
                } else {
                    state.blockedNotifications.takeLast(8).asReversed().forEach { item ->
                        Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            Text(item.title.ifBlank { item.packageName }, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (item.body.isNotBlank()) Text(item.body, color = HarborMuted, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                            Text("${item.packageName} · ${DateTimeFormatter.ofPattern("MMM d, h:mm a", Locale.getDefault()).format(java.time.Instant.ofEpochMilli(item.capturedAtEpochMillis).atZone(java.time.ZoneId.systemDefault()))}", color = HarborMuted, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }

        Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = HarborMint)) {
            Column(Modifier.padding(17.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("A note about Android controls", fontWeight = FontWeight.Bold, color = HarborInk)
                Text(
                    "This app has no Internet permission, account, analytics, ads or cloud backup. Android Settings, force-stop, uninstall and some browser/OEM behavior cannot be reliably blocked by an ordinary app. Accessibility and notification access can be revoked at any time.",
                    color = HarborInk,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }

    if (ignoredPickerOpen) {
        AppPickerDialog(
            title = "Ignore apps in statistics",
            detail = "These apps still follow blocking rules, but their usage and launch counters are omitted.",
            apps = installedApps,
            initialSelection = state.settings.ignoredStatisticsPackages,
            onDismiss = { ignoredPickerOpen = false },
            onSave = { selected ->
                onSettingsChange { it.copy(settings = it.settings.copy(ignoredStatisticsPackages = selected)) }
                ignoredPickerOpen = false
            },
        )
    }
}

@Composable
private fun PermissionRow(title: String, detail: String, enabled: Boolean, action: () -> Unit, buttonText: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(if (enabled) HarborTeal else HarborGold))
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(detail, color = HarborMuted, style = MaterialTheme.typography.bodySmall)
        }
        TextButton(onClick = action) { Text(buttonText) }
    }
}

@Composable
private fun SettingToggle(title: String, detail: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(detail, color = HarborMuted, style = MaterialTheme.typography.bodySmall)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun QuickTargetsDialog(
    state: FocusAppState,
    installedApps: List<LaunchableApp>,
    onDismiss: () -> Unit,
    onSave: (Set<String>, Set<String>, Long) -> Unit,
) {
    var selectedApps by remember(state.settings.quickBlockApps) { mutableStateOf(state.settings.quickBlockApps) }
    var domains by remember(state.settings.quickBlockDomains) { mutableStateOf(state.settings.quickBlockDomains.sorted().joinToString("\n")) }
    var minutes by remember(state.settings.quickBlockMinutes) { mutableStateOf(state.settings.quickBlockMinutes) }
    var appPickerOpen by rememberSaveable { mutableStateOf(false) }
    var validation by remember { mutableStateOf<String?>(null) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxWidth(0.95f).heightIn(max = 720.dp), shape = RoundedCornerShape(28.dp), color = Color.White) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Quick Block targets", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("These targets are used by the home screen, Quick Settings tile and widget.", color = HarborMuted, style = MaterialTheme.typography.bodySmall)
                OutlinedButton(onClick = { appPickerOpen = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("Choose apps · ${selectedApps.size} selected")
                }
                OutlinedField(
                    value = domains,
                    onValueChange = { domains = it },
                    label = "Website domains · one per line",
                    minLines = 3,
                )
                Text("Session length", fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    listOf(15L, 25L, 45L, 60L).forEach { duration ->
                        FilterChip(selected = minutes == duration, onClick = { minutes = duration }, label = { Text("${duration}m") })
                    }
                }
                validation?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        val values = domains.lines().map(String::trim).filter(String::isNotEmpty)
                        val normalized = values.mapNotNull(DomainMatcher::normalize)
                        if (normalized.size != values.size) validation = "Check the website hostnames and try again."
                        else if (selectedApps.isEmpty() && normalized.isEmpty()) validation = "Choose at least one app or domain."
                        else onSave(selectedApps, normalized.toSet(), minutes)
                    }) { Text("Save targets") }
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                }
            }
        }
    }
    if (appPickerOpen) {
        AppPickerDialog(
            title = "Quick Block apps",
            detail = "Select the apps that should pause during a Quick Block session.",
            apps = installedApps,
            initialSelection = selectedApps,
            onDismiss = { appPickerOpen = false },
            onSave = { selectedApps = it; appPickerOpen = false },
        )
    }
}

@Composable
private fun StrictReleaseDialog(profile: FocusProfile, onDismiss: () -> Unit, onRelease: (String) -> Unit) {
    var pin by rememberSaveable(profile.id) { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Release Strict Mode") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("The selected timer, schedule, charger, cooldown and PIN conditions must all be satisfied.", color = HarborMuted)
                if (profile.strictConfig.requirePin) {
                    androidx.compose.material3.OutlinedTextField(
                        value = pin,
                        onValueChange = { if (it.length <= 12 && it.all(Char::isDigit)) pin = it },
                        label = { Text("4–12 digit PIN") },
                        singleLine = true,
                    )
                }
                Text("If a required condition is not met, Focus Harbor will explain why.", style = MaterialTheme.typography.bodySmall, color = HarborMuted)
            }
        },
        confirmButton = { Button(onClick = { onRelease(pin) }) { Text("Check conditions") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Not now") } },
    )
}

@Composable
private fun EmergencyUnlockDialog(
    profile: FocusProfile,
    onDismiss: () -> Unit,
    onBegin: () -> Unit,
    onComplete: (String, String) -> Unit,
) {
    var reason by rememberSaveable(profile.id) { mutableStateOf("") }
    var impact by rememberSaveable(profile.id) { mutableStateOf("") }
    var alternative by rememberSaveable(profile.id) { mutableStateOf("") }
    var commitment by rememberSaveable(profile.id) { mutableStateOf("") }
    var tick by remember { mutableStateOf(SystemClock.elapsedRealtime()) }
    val session = profile.strictSession
    LaunchedEffect(session?.emergencyStartedAtElapsedMillis) {
        while (true) {
            tick = SystemClock.elapsedRealtime()
            delay(1_000L)
        }
    }
    val startedElapsed = session?.emergencyStartedAtElapsedMillis
    val startedEpoch = session?.emergencyStartedAtEpochMillis
    val cooldownMs = profile.strictConfig.emergencyCooldownMinutes * 60_000L
    val elapsed = when {
        startedElapsed != null && tick >= startedElapsed -> tick - startedElapsed
        startedEpoch != null -> (System.currentTimeMillis() - startedEpoch).coerceAtLeast(0L)
        else -> 0L
    }
    val remaining = (cooldownMs - elapsed).coerceAtLeast(0L)
    val reflection = listOf(reason.trim(), impact.trim(), alternative.trim()).joinToString("\u001F")
    val ready = reason.trim().length >= 10 && impact.trim().length >= 10 && alternative.trim().length >= 10 &&
        commitment.trim() == FocusViewModel.EMERGENCY_COMMITMENT && startedElapsed != null && remaining == 0L

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Emergency release") },
        text = {
            Column(Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Text("This is a deliberate fallback. The emergency cooldown is ${profile.strictConfig.emergencyCooldownMinutes} minute(s); it does not make Android controls unbypassable.", color = HarborMuted, style = MaterialTheme.typography.bodySmall)
                OutlinedField(reason, { reason = it }, "What is making you want to leave this session?", minLines = 2)
                OutlinedField(impact, { impact = it }, "What could happen if you wait until the session ends?", minLines = 2)
                OutlinedField(alternative, { alternative = it }, "What is one useful next step that does not require leaving focus?", minLines = 2)
                if (startedElapsed == null) {
                    Text("Start the cooldown only after considering the questions above.", color = HarborTeal, style = MaterialTheme.typography.bodySmall)
                } else {
                    Text("Emergency cooldown remaining: ${formatClock(remaining)}", color = HarborTeal, fontWeight = FontWeight.Bold)
                }
                OutlinedField(commitment, { commitment = it }, "Type the commitment exactly to continue", minLines = 2)
                Text(FocusViewModel.EMERGENCY_COMMITMENT, color = HarborMuted, style = MaterialTheme.typography.labelSmall)
            }
        },
        confirmButton = {
            if (startedElapsed == null) {
                Button(
                    onClick = onBegin,
                    enabled = reason.trim().length >= 10 && impact.trim().length >= 10 && alternative.trim().length >= 10,
                ) { Text("Begin cooldown") }
            } else {
                Button(onClick = { onComplete(reflection, commitment) }, enabled = ready) { Text("Release after reflection") }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Keep Strict Mode") } },
    )
}

@Composable
private fun SectionHeading(title: String, detail: String) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = HarborInk)
        Text(detail, color = HarborMuted, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ActionCard(icon: ImageVector, title: String, detail: String, actionLabel: String, onClick: () -> Unit) {
    Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Row(Modifier.fillMaxWidth().padding(17.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(13.dp)) {
            Surface(color = HarborMint, shape = CircleShape, modifier = Modifier.size(42.dp)) {
                Box(contentAlignment = Alignment.Center) { Icon(icon, contentDescription = null, tint = HarborTeal) }
            }
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold)
                Text(detail, color = HarborMuted, style = MaterialTheme.typography.bodySmall)
            }
            TextButton(onClick = onClick) { Text(actionLabel) }
        }
    }
}

@Composable
private fun EmptyCard(title: String, detail: String) {
    Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, fontWeight = FontWeight.Bold)
            Text(detail, color = HarborMuted, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun NoteCard(text: String) {
    Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFE5EFEC))) {
        Text(text, Modifier.padding(15.dp), color = HarborMuted, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun StatPill(text: String) {
    Surface(color = HarborCanvas, shape = CircleShape) {
        Text(text, Modifier.padding(horizontal = 10.dp, vertical = 6.dp), style = MaterialTheme.typography.labelSmall, color = HarborInk)
    }
}

@Composable
private fun MetricCard(title: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier, shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text(title, style = MaterialTheme.typography.labelMedium, color = HarborMuted)
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = HarborTeal)
        }
    }
}

@Composable
private fun OutlinedField(value: String, onValueChange: (String) -> Unit, label: String, minLines: Int = 1) {
    androidx.compose.material3.OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        minLines = minLines,
        maxLines = if (minLines == 1) 1 else 5,
    )
}


private fun formatClock(millis: Long): String {
    val totalSeconds = (millis / 1_000L).coerceAtLeast(0L)
    val hours = totalSeconds / 3_600L
    val minutes = (totalSeconds / 60L) % 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, seconds)
    else String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
}

private fun formatMinutes(minutes: Long): String = when {
    minutes <= 0 -> "0m"
    minutes < 60 -> "${minutes}m"
    else -> "${minutes / 60}h ${minutes % 60}m"
}
