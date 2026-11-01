package com.v1i2c.focusharbor.ui

import android.content.Context
import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.v1i2c.focusharbor.data.FocusRepository
import com.v1i2c.focusharbor.data.StateOperations
import com.v1i2c.focusharbor.domain.BackupCodec
import com.v1i2c.focusharbor.domain.FocusAppState
import com.v1i2c.focusharbor.domain.FocusProfile
import com.v1i2c.focusharbor.domain.PinHasher
import com.v1i2c.focusharbor.domain.QuickBlockSession
import com.v1i2c.focusharbor.domain.StrictModeGate
import com.v1i2c.focusharbor.domain.StrictModeRequirements
import com.v1i2c.focusharbor.domain.StrictModeSession
import com.v1i2c.focusharbor.domain.StrictReleaseContext
import com.v1i2c.focusharbor.domain.WebsiteCsvCodec
import com.v1i2c.focusharbor.domain.DomainMatcher
import com.v1i2c.focusharbor.platform.AndroidTriggers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.ZonedDateTime

class FocusViewModel(
    private val repository: FocusRepository,
    context: Context,
) : ViewModel() {
    private val appContext = context.applicationContext
    val state = repository.state

    private val _message = MutableStateFlow<String?>(null)
    val message = _message.asStateFlow()

    fun dismissMessage() { _message.value = null }

    private fun showMessage(text: String) { _message.value = text }

    fun saveProfile(profile: FocusProfile, newPin: String? = null) {
        viewModelScope.launch {
            val current = repository.state.value.profiles.firstOrNull { it.id == profile.id }
            if (current?.strictSession != null && current.strictConfig.lockEditing) {
                showMessage("Profile changes are locked by Strict Mode. Use the emergency release flow if needed.")
                return@launch
            }
            val enteredPin = newPin?.trim().orEmpty()
            val verifier = if (enteredPin.isNotEmpty()) {
                val pinChars = enteredPin.toCharArray()
                try {
                    withContext(Dispatchers.Default) { PinHasher.create(pinChars) }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    showMessage(error.message ?: "Could not set the PIN.")
                    return@launch
                } finally {
                    pinChars.fill('\u0000')
                }
            } else {
                profile.strictConfig.pinVerifier
            }
            if (profile.strictConfig.requirePin && verifier == null) {
                showMessage("Set a PIN before requiring PIN confirmation.")
                return@launch
            }
            val updated = profile.copy(strictConfig = profile.strictConfig.copy(pinVerifier = verifier))
            repository.update { StateOperations.saveProfile(it, updated) }
        }
    }

    fun toggleProfile(profileId: String, enabled: Boolean) {
        viewModelScope.launch {
            repository.update { current ->
                current.copy(profiles = current.profiles.map { profile ->
                    if (profile.id != profileId || profile.strictSession != null) profile
                    else profile.copy(enabled = enabled, pauseUntilEpochMillis = null)
                })
            }
        }
    }

    fun deleteProfile(profileId: String) {
        viewModelScope.launch {
            repository.update { current ->
                val profile = current.profiles.firstOrNull { it.id == profileId }
                if (profile?.strictSession != null) {
                    showMessage("A Strict Mode profile cannot be deleted until it is released.")
                    current
                } else {
                    current.copy(profiles = current.profiles.filterNot { it.id == profileId })
                }
            }
        }
    }

    fun duplicateProfile(profileId: String) {
        viewModelScope.launch {
            repository.update { current ->
                val original = current.profiles.firstOrNull { it.id == profileId } ?: return@update current
                if (original.strictSession != null) {
                    showMessage("Release Strict Mode before duplicating this profile.")
                    return@update current
                }
                val duplicate = original.copy(
                    id = java.util.UUID.randomUUID().toString(),
                    name = "${original.name} copy".take(80),
                    enabled = false,
                    strictSession = null,
                )
                current.copy(profiles = current.profiles + duplicate)
            }
        }
    }

    fun pauseProfile(profileId: String, minutes: Long) {
        viewModelScope.launch {
            repository.update { current ->
                current.copy(profiles = current.profiles.map { profile ->
                    if (profile.id != profileId || profile.strictSession != null) profile
                    else profile.copy(pauseUntilEpochMillis = System.currentTimeMillis() + minutes.coerceAtLeast(1L) * 60_000L)
                })
            }
        }
    }

    fun resumeProfile(profileId: String) {
        viewModelScope.launch {
            repository.update { current ->
                current.copy(profiles = current.profiles.map { profile ->
                    if (profile.id == profileId && profile.strictSession == null) profile.copy(pauseUntilEpochMillis = null) else profile
                })
            }
        }
    }

    fun startQuickBlock(minutes: Long, apps: Set<String>, domains: Set<String>) {
        val normalizedDomains = domains.mapNotNull(DomainMatcher::normalize).toSet()
        if (apps.isEmpty() && normalizedDomains.isEmpty()) {
            showMessage("Choose at least one app or website to Quick Block.")
            return
        }
        val safeMinutes = minutes.coerceIn(1L, 365L * 24L * 60L)
        val nowEpoch = System.currentTimeMillis()
        val nowElapsed = SystemClock.elapsedRealtime()
        viewModelScope.launch {
            repository.update { current ->
                current.copy(
                    quickBlock = QuickBlockSession(
                        startedAtEpochMillis = nowEpoch,
                        startedAtElapsedMillis = nowElapsed,
                        durationMillis = safeMinutes * 60_000L,
                        appPackages = apps,
                        websiteDomains = normalizedDomains,
                    ),
                )
            }
            refreshWidget()
        }
    }

    fun stopQuickBlock() {
        viewModelScope.launch {
            repository.update { it.copy(quickBlock = null) }
            refreshWidget()
        }
    }

    suspend fun toggleQuickBlockFromSystemSurface(): Boolean {
        repository.awaitReady()
        val current = repository.state.value
        val session = current.quickBlock
        val nowEpoch = System.currentTimeMillis()
        val nowElapsed = SystemClock.elapsedRealtime()
        if (session != null && !session.isExpired(nowEpoch, nowElapsed)) {
            repository.update { it.copy(quickBlock = null) }
            refreshWidget()
            return false
        }
        if (current.settings.quickBlockApps.isEmpty() && current.settings.quickBlockDomains.isEmpty()) {
            showMessage("Choose Quick Block apps or websites in Focus Harbor first.")
            return false
        }
        val minutes = current.settings.quickBlockMinutes.coerceIn(1L, 365L * 24L * 60L)
        repository.update {
            it.copy(quickBlock = QuickBlockSession(
                startedAtEpochMillis = nowEpoch,
                startedAtElapsedMillis = nowElapsed,
                durationMillis = minutes * 60_000L,
                appPackages = current.settings.quickBlockApps,
                websiteDomains = current.settings.quickBlockDomains.mapNotNull(DomainMatcher::normalize).toSet(),
            ))
        }
        refreshWidget()
        return true
    }

    fun handleQuickBlockSurfaceIntent() {
        viewModelScope.launch { toggleQuickBlockFromSystemSurface() }
    }

    private fun refreshWidget() {
        runCatching { com.v1i2c.focusharbor.widget.FocusHarborWidgetProvider.updateAll(appContext) }
    }

    fun startStrictMode(profileId: String) {
        viewModelScope.launch {
            val nowEpoch = System.currentTimeMillis()
            val nowElapsed = SystemClock.elapsedRealtime()
            repository.update { current ->
                val profile = current.profiles.firstOrNull { it.id == profileId } ?: return@update current
                if (!profile.strictConfig.enabled) {
                    showMessage("Enable Strict Mode in this profile before starting it.")
                    return@update current
                }
                if (profile.strictSession != null) {
                    showMessage("Strict Mode is already running for this profile.")
                    return@update current
                }
                if (profile.strictConfig.requirePin && profile.strictConfig.pinVerifier == null) {
                    showMessage("Set a Strict Mode PIN before starting this profile.")
                    return@update current
                }
                val timerMillis = profile.strictConfig.timerMinutes?.times(60_000L)
                val session = StrictModeSession(
                    startedAtEpochMillis = nowEpoch,
                    startedAtElapsedMillis = nowElapsed,
                    timerEndsAtEpochMillis = timerMillis?.let { nowEpoch + it },
                    releaseCooldownUntilEpochMillis = if (profile.strictConfig.cooldownMinutes > 0L) {
                        nowEpoch + profile.strictConfig.cooldownMinutes * 60_000L
                    } else {
                        null
                    },
                )
                current.copy(profiles = current.profiles.map { if (it.id == profileId) it.copy(enabled = true, strictSession = session) else it })
            }
        }
    }

    fun extendStrictTimer(profileId: String, minutes: Long = 25L) {
        viewModelScope.launch {
            val nowEpoch = System.currentTimeMillis()
            val nowElapsed = SystemClock.elapsedRealtime()
            repository.update { current ->
                current.copy(profiles = current.profiles.map { profile ->
                    if (profile.id != profileId || profile.strictSession == null) return@map profile
                    val session = profile.strictSession
                    val effectiveNow = effectiveNowMillis(session.startedAtEpochMillis, session.startedAtElapsedMillis, nowEpoch, nowElapsed)
                    val newEnd = maxOf(session.timerEndsAtEpochMillis ?: effectiveNow, effectiveNow) + minutes.coerceAtLeast(1L) * 60_000L
                    profile.copy(strictSession = session.copy(timerEndsAtEpochMillis = newEnd))
                })
            }
        }
    }

    fun releaseStrictMode(profileId: String, enteredPin: String) {
        viewModelScope.launch {
            val profile = repository.state.value.profiles.firstOrNull { it.id == profileId } ?: return@launch
            val session = profile.strictSession ?: return@launch
            val pinAccepted = if (profile.strictConfig.requirePin) {
                val pinChars = enteredPin.toCharArray()
                try {
                    withContext(Dispatchers.Default) { PinHasher.verify(pinChars, profile.strictConfig.pinVerifier) }
                } finally {
                    pinChars.fill('\u0000')
                }
            } else {
                true
            }
            val nowEpoch = System.currentTimeMillis()
            val nowElapsed = SystemClock.elapsedRealtime()
            val effectiveNow = effectiveNowMillis(session.startedAtEpochMillis, session.startedAtElapsedMillis, nowEpoch, nowElapsed)
            val scheduleActive = profile.schedules.any { it.isActiveAt(ZonedDateTime.now()) }
            val ready = StrictModeGate.canDeactivate(
                requirements = StrictModeRequirements(
                    timerEndsAtMillis = session.timerEndsAtEpochMillis,
                    requireScheduleEnd = profile.strictConfig.requireScheduleEnd,
                    requireCharger = profile.strictConfig.requireCharger,
                    requirePin = profile.strictConfig.requirePin,
                    cooldownUntilMillis = session.releaseCooldownUntilEpochMillis,
                ),
                context = StrictReleaseContext(
                    nowMillis = effectiveNow,
                    scheduleActive = scheduleActive,
                    chargerConnected = AndroidTriggers.isCharging(appContext),
                    pinAccepted = pinAccepted,
                ),
            )
            if (!ready) {
                val reason = when {
                    profile.strictConfig.requirePin && !pinAccepted -> "The PIN does not match."
                    profile.strictConfig.requireCharger && !AndroidTriggers.isCharging(appContext) -> "Connect the device to power to release Strict Mode."
                    profile.strictConfig.requireScheduleEnd && scheduleActive -> "This profile’s schedule is still active."
                    session.timerEndsAtEpochMillis?.let { effectiveNow < it } == true -> "The Strict Mode timer is still running."
                    else -> "The release cooldown is still running."
                }
                showMessage(reason)
                return@launch
            }
            repository.update { current ->
                current.copy(profiles = current.profiles.map { if (it.id == profileId) it.copy(strictSession = null) else it })
            }
        }
    }

    fun beginEmergencyUnlock(profileId: String) {
        val nowEpoch = System.currentTimeMillis()
        val nowElapsed = SystemClock.elapsedRealtime()
        viewModelScope.launch {
            repository.update { current ->
                current.copy(profiles = current.profiles.map { profile ->
                    if (profile.id != profileId) profile
                    else {
                        val session = profile.strictSession ?: return@map profile
                        if (session.emergencyStartedAtEpochMillis != null) profile
                        else profile.copy(strictSession = session.copy(
                            emergencyStartedAtEpochMillis = nowEpoch,
                            emergencyStartedAtElapsedMillis = nowElapsed,
                        ))
                    }
                })
            }
        }
    }

    fun completeEmergencyUnlock(profileId: String, typedCommitment: String, reflection: String) {
        viewModelScope.launch {
            val answers = reflection.split('\u001F').map(String::trim)
            if (answers.size < 3 || answers.take(3).any { it.length < 10 }) {
                showMessage("Complete each reflection prompt before continuing.")
                return@launch
            }
            val profile = repository.state.value.profiles.firstOrNull { it.id == profileId } ?: return@launch
            val session = profile.strictSession ?: return@launch
            val startEpoch = session.emergencyStartedAtEpochMillis ?: run {
                showMessage("Start the emergency-unlock cooldown first.")
                return@launch
            }
            val startElapsed = session.emergencyStartedAtElapsedMillis ?: startEpoch
            val nowEpoch = System.currentTimeMillis()
            val nowElapsed = SystemClock.elapsedRealtime()
            val effectiveNow = effectiveNowMillis(startEpoch, startElapsed, nowEpoch, nowElapsed)
            val accepted = StrictModeGate.canEmergencyRelease(
                emergencyStartedAtMillis = startEpoch,
                cooldownMinutes = profile.strictConfig.emergencyCooldownMinutes,
                nowMillis = effectiveNow,
                commitmentMatches = typedCommitment.trim() == EMERGENCY_COMMITMENT,
            )
            if (!accepted) {
                showMessage("Complete the reflection steps, type the commitment exactly, and wait for the emergency cooldown to finish.")
                return@launch
            }
            repository.update { current ->
                current.copy(profiles = current.profiles.map { if (it.id == profileId) it.copy(strictSession = null) else it })
            }
        }
    }

    fun updateSettings(transform: (FocusAppState) -> FocusAppState) {
        viewModelScope.launch {
            repository.update(transform)
            refreshWidget()
        }
    }

    fun importBackup(raw: String) {
        viewModelScope.launch {
            repository.awaitReady()
            val lockedStrictMode = repository.state.value.profiles.any {
                it.strictSession != null && it.strictConfig.lockEditing
            }
            if (lockedStrictMode) {
                showMessage("A backup cannot replace a locked Strict Mode profile. Use its release flow first.")
                return@launch
            }
            try {
                val imported = withContext(Dispatchers.Default) { BackupCodec.decode(raw) }
                repository.replace(imported)
                showMessage("Local backup imported.")
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                showMessage(error.message ?: "Could not import this backup.")
            }
        }
    }

    fun exportBackup(): String = BackupCodec.encode(repository.state.value)

    fun exportWebsiteCsv(): String = WebsiteCsvCodec.encodeDomains(repository.state.value.settings.adultDomains)

    fun importWebsiteCsv(raw: String) {
        viewModelScope.launch {
            try {
                val domains = withContext(Dispatchers.Default) { WebsiteCsvCodec.parseDomains(raw) }
                if (domains.isEmpty()) {
                    showMessage("The CSV did not contain any website domains.")
                    return@launch
                }
                repository.update { current ->
                    current.copy(settings = current.settings.copy(adultDomains = current.settings.adultDomains + domains))
                }
                showMessage("Imported ${domains.size} local website domains.")
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                showMessage(error.message ?: "Could not import the website list.")
            }
        }
    }

    fun clearBlockedNotifications() {
        viewModelScope.launch { repository.update { it.copy(blockedNotifications = emptyList()) } }
    }

    companion object {
        const val EMERGENCY_COMMITMENT = "I acknowledge that I am breaking my focus commitment and taking responsibility for this distraction."

        private fun effectiveNowMillis(startEpoch: Long, startElapsed: Long, nowEpoch: Long, nowElapsed: Long): Long =
            if (nowElapsed >= startElapsed) startEpoch + (nowElapsed - startElapsed) else nowEpoch
    }

    class Factory(
        private val repository: FocusRepository,
        private val context: Context,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(FocusViewModel::class.java)) {
                return FocusViewModel(repository, context) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
