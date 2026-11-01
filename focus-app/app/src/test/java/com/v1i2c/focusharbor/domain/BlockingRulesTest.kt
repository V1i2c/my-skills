package com.v1i2c.focusharbor.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.ZoneOffset
import java.time.ZonedDateTime

class BlockingRulesTest {
    @Test
    fun `scheduled interval includes its start and excludes its end`() {
        val rule = ScheduleRule(
            daysOfWeek = setOf(DayOfWeek.MONDAY.value),
            startMinute = 9 * 60,
            endMinute = 10 * 60,
        )

        assertTrue(rule.isActiveAt(at(DayOfWeek.MONDAY, 9 * 60)))
        assertTrue(rule.isActiveAt(at(DayOfWeek.MONDAY, 9 * 60 + 59)))
        assertFalse(rule.isActiveAt(at(DayOfWeek.MONDAY, 10 * 60)))
        assertFalse(rule.isActiveAt(at(DayOfWeek.TUESDAY, 9 * 60)))
    }

    @Test
    fun `overnight interval belongs to its start day after midnight`() {
        val rule = ScheduleRule(
            daysOfWeek = setOf(DayOfWeek.MONDAY.value),
            startMinute = 22 * 60,
            endMinute = 6 * 60,
        )

        assertTrue(rule.isActiveAt(at(DayOfWeek.MONDAY, 23 * 60)))
        assertTrue(rule.isActiveAt(at(DayOfWeek.TUESDAY, 5 * 60 + 59)))
        assertFalse(rule.isActiveAt(at(DayOfWeek.TUESDAY, 6 * 60)))
        assertFalse(rule.isActiveAt(at(DayOfWeek.SUNDAY, 23 * 60)))
    }

    @Test
    fun `website matching is host-boundary aware`() {
        assertEquals("reddit.com", DomainMatcher.normalize(" HTTPS://WWW.Reddit.com/r/kotlin?sort=top "))
        assertTrue(DomainMatcher.matches("reddit.com", "reddit.com"))
        assertTrue(DomainMatcher.matches("m.reddit.com", "reddit.com"))
        assertFalse(DomainMatcher.matches("notreddit.com", "reddit.com"))
    }

    @Test
    fun `strict release requires every selected condition`() {
        val requirements = StrictModeRequirements(
            timerEndsAtMillis = 10_000L,
            requireScheduleEnd = true,
            requireCharger = true,
            requirePin = true,
            cooldownUntilMillis = 12_000L,
        )

        assertFalse(
            StrictModeGate.canDeactivate(
                requirements,
                StrictReleaseContext(
                    nowMillis = 11_000L,
                    scheduleActive = false,
                    chargerConnected = true,
                    pinAccepted = true,
                ),
            ),
        )
        assertTrue(
            StrictModeGate.canDeactivate(
                requirements,
                StrictReleaseContext(
                    nowMillis = 12_000L,
                    scheduleActive = false,
                    chargerConnected = true,
                    pinAccepted = true,
                ),
            ),
        )
    }

    @Test
    fun `strict release refuses a missing pin or charger`() {
        val requirements = StrictModeRequirements(
            requireCharger = true,
            requirePin = true,
        )

        assertFalse(
            StrictModeGate.canDeactivate(
                requirements,
                StrictReleaseContext(0L, scheduleActive = false, chargerConnected = false, pinAccepted = true),
            ),
        )
        assertFalse(
            StrictModeGate.canDeactivate(
                requirements,
                StrictReleaseContext(0L, scheduleActive = false, chargerConnected = true, pinAccepted = false),
            ),
        )
    }

    @Test
    fun `quick block timer uses monotonic time while the device clock changes`() {
        val session = QuickBlockSession(
            startedAtEpochMillis = 5_000L,
            startedAtElapsedMillis = 1_000L,
            durationMillis = 10_000L,
        )

        assertEquals(6_000L, session.remainingMillis(nowEpochMillis = 500_000L, nowElapsedMillis = 5_000L))
    }

    @Test
    fun `allowlist allows selected packages and blocks all other packages`() {
        val state = FocusAppState(profiles = listOf(
            FocusProfile(
                id = "allow-list",
                enabled = true,
                allowList = true,
                appPackages = setOf("com.example.reader"),
            ),
        ))
        val now = ZonedDateTime.of(2026, 1, 5, 9, 0, 0, 0, ZoneOffset.UTC)

        assertFalse(BlockingDecisionEngine.shouldBlockPackage(state, "com.example.reader", now, nowElapsedMillis = 1_000L).blocked)
        assertTrue(BlockingDecisionEngine.shouldBlockPackage(state, "com.example.social", now, nowElapsedMillis = 1_000L).blocked)
    }

    @Test
    fun `website allowlist blocks unlisted hostnames only`() {
        val state = FocusAppState(profiles = listOf(
            FocusProfile(
                id = "web-allow-list",
                enabled = true,
                allowList = true,
                websiteDomains = setOf("example.org"),
            ),
        ))
        val now = ZonedDateTime.of(2026, 1, 5, 9, 0, 0, 0, ZoneOffset.UTC)

        assertFalse(BlockingDecisionEngine.shouldBlockWebsite(state, "docs.example.org", now, nowElapsedMillis = 1_000L).blocked)
        assertTrue(BlockingDecisionEngine.shouldBlockWebsite(state, "social.example", now, nowElapsedMillis = 1_000L).blocked)
    }

    @Test
    fun `quota evaluator uses the matching period record`() {
        val profile = FocusProfile(
            id = "weekly-limit",
            enabled = true,
            quotas = mapOf("com.example.reader" to UsageQuota(maximumMinutes = 10L, period = QuotaPeriod.WEEKLY)),
        )
        val now = ZonedDateTime.of(2026, 1, 5, 9, 0, 0, 0, ZoneOffset.UTC)
        val dailyRecord = UsageRecord(
            periodKey = "2026-01-05",
            packageName = "com.example.reader",
            period = QuotaPeriod.DAILY,
            foregroundMillis = 30 * 60_000L,
        )
        val state = FocusAppState(profiles = listOf(profile), usage = listOf(dailyRecord))
        assertFalse(BlockingDecisionEngine.shouldBlockPackage(state, "com.example.reader", now, nowElapsedMillis = 1_000L).blocked)

        val weeklyRecord = dailyRecord.copy(period = QuotaPeriod.WEEKLY, foregroundMillis = 10 * 60_000L)
        val withWeeklyUsage = state.copy(usage = listOf(dailyRecord, weeklyRecord))
        assertTrue(BlockingDecisionEngine.shouldBlockPackage(withWeeklyUsage, "com.example.reader", now, nowElapsedMillis = 1_000L).blocked)
    }

    private fun at(day: DayOfWeek, minute: Int): ZonedDateTime {
        val monday = ZonedDateTime.of(2026, 1, 5, 0, 0, 0, 0, ZoneOffset.UTC)
        val dayOffset = day.value - DayOfWeek.MONDAY.value
        return monday.plusDays(dayOffset.toLong()).plusMinutes(minute.toLong())
    }
}
