package com.v1i2c.focusharbor.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.v1i2c.focusharbor.domain.FocusAppState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.focusDataStore by preferencesDataStore(name = "focus_harbor_state")

class FocusRepository(context: Context) {
    private val store = context.applicationContext.focusDataStore
    private val stateKey = stringPreferencesKey("state_v1")
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }
    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val ready = CompletableDeferred<Unit>()

    val state = store.data
        .map { preferences -> decode(preferences[stateKey]) }
        .catch { emit(FocusAppState()) }
        .onEach { ready.complete(Unit) }
        .stateIn(repositoryScope, SharingStarted.Eagerly, FocusAppState())

    suspend fun awaitReady() { ready.await() }

    suspend fun update(transform: (FocusAppState) -> FocusAppState): FocusAppState {
        var updated: FocusAppState? = null
        store.edit { preferences ->
            val current = decode(preferences[stateKey])
            val next = transform(current).copy(schemaVersion = FocusAppState.CURRENT_SCHEMA_VERSION)
            preferences[stateKey] = json.encodeToString(next)
            updated = next
        }
        return requireNotNull(updated)
    }

    suspend fun replace(state: FocusAppState): FocusAppState {
        val replacement = state.copy(schemaVersion = FocusAppState.CURRENT_SCHEMA_VERSION)
        store.edit { preferences ->
            preferences[stateKey] = json.encodeToString(replacement)
        }
        return replacement
    }

    suspend fun awaitState(expected: FocusAppState) {
        state.first { it == expected }
    }

    fun encode(state: FocusAppState): String = json.encodeToString(state)

    fun decodeBackup(raw: String): FocusAppState = json.decodeFromString(raw)

    private fun decode(raw: String?): FocusAppState {
        if (raw.isNullOrBlank()) return FocusAppState()
        return runCatching { json.decodeFromString<FocusAppState>(raw) }
            .getOrElse { FocusAppState() }
    }
}
