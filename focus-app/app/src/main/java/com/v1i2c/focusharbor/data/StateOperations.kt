package com.v1i2c.focusharbor.data

import com.v1i2c.focusharbor.domain.DailyStats
import com.v1i2c.focusharbor.domain.FocusAppState
import com.v1i2c.focusharbor.domain.FocusProfile
import com.v1i2c.focusharbor.domain.QuotaPeriod
import com.v1i2c.focusharbor.domain.UsageRecord
import java.time.LocalDate

private const val MAX_USAGE_RECORDS = 10_000
private const val MAX_DAILY_STAT_DAYS = 730

object StateOperations {
    fun saveProfile(state: FocusAppState, profile: FocusProfile): FocusAppState {
        val index = state.profiles.indexOfFirst { it.id == profile.id }
        val profiles = if (index < 0) state.profiles + profile else state.profiles.toMutableList().also { it[index] = profile }
        return state.copy(profiles = profiles)
    }

    fun recordUsage(
        state: FocusAppState,
        packageName: String,
        periodKey: String,
        period: QuotaPeriod = QuotaPeriod.DAILY,
        foregroundDeltaMillis: Long = 0L,
        launchDelta: Int = 0,
        blockedDelta: Int = 0,
        dateKey: String = LocalDate.now().toString(),
        recordFocus: Boolean = true,
    ): FocusAppState {
        if (packageName.isBlank()) return state
        val index = state.usage.indexOfFirst { it.packageName == packageName && it.periodKey == periodKey && it.period == period }
        val record = if (index < 0) {
            UsageRecord(
                periodKey = periodKey,
                packageName = packageName,
                period = period,
                foregroundMillis = foregroundDeltaMillis.coerceAtLeast(0L),
                launches = launchDelta.coerceAtLeast(0),
                blockedAttempts = blockedDelta.coerceAtLeast(0),
            )
        } else {
            state.usage[index].copy(
                foregroundMillis = (state.usage[index].foregroundMillis + foregroundDeltaMillis).coerceAtLeast(0L),
                launches = (state.usage[index].launches + launchDelta).coerceAtLeast(0),
                blockedAttempts = (state.usage[index].blockedAttempts + blockedDelta).coerceAtLeast(0),
            )
        }
        val usage = if (index < 0) state.usage + record else state.usage.toMutableList().also { it[index] = record }
        return state.copy(
            usage = if (usage.size > MAX_USAGE_RECORDS) usage.takeLast(MAX_USAGE_RECORDS) else usage,
            dailyStats = if (recordFocus) {
                updateDailyFocus(state.dailyStats, dateKey, foregroundDeltaMillis)
            } else {
                state.dailyStats
            },
        )
    }

    fun recordScreenUnlock(state: FocusAppState, dateKey: String = LocalDate.now().toString()): FocusAppState {
        val index = state.dailyStats.indexOfFirst { it.dateKey == dateKey }
        val updated = if (index < 0) {
            DailyStats(dateKey = dateKey, screenUnlocks = 1)
        } else {
            state.dailyStats[index].copy(screenUnlocks = state.dailyStats[index].screenUnlocks + 1)
        }
        val stats = if (index < 0) state.dailyStats + updated else state.dailyStats.toMutableList().also { it[index] = updated }
        return state.copy(dailyStats = if (stats.size > MAX_DAILY_STAT_DAYS) stats.takeLast(MAX_DAILY_STAT_DAYS) else stats)
    }

    private fun updateDailyFocus(stats: List<DailyStats>, dateKey: String, deltaMillis: Long): List<DailyStats> {
        if (deltaMillis <= 0L) return stats
        val index = stats.indexOfFirst { it.dateKey == dateKey }
        val previous = stats.getOrNull(index)
        val accumulatedMillis = (previous?.focusMillisRemainder ?: 0L) + deltaMillis.coerceAtLeast(0L)
        val addMinutes = accumulatedMillis / 60_000L
        val updated = if (previous == null) {
            DailyStats(
                dateKey = dateKey,
                focusMinutes = addMinutes,
                focusMillisRemainder = accumulatedMillis % 60_000L,
            )
        } else {
            previous.copy(
                focusMinutes = previous.focusMinutes + addMinutes,
                focusMillisRemainder = accumulatedMillis % 60_000L,
            )
        }
        val result = if (index < 0) stats + updated else stats.toMutableList().also { it[index] = updated }
        return if (result.size > MAX_DAILY_STAT_DAYS) result.takeLast(MAX_DAILY_STAT_DAYS) else result
    }
}
