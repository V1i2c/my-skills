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
            daysOfWeek = setOf(DayOfWeek.MONDAY),
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
            daysOfWeek = setOf(DayOfWeek.MONDAY),
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

    private fun at(day: DayOfWeek, minute: Int): ZonedDateTime {
        val monday = ZonedDateTime.of(2026, 1, 5, 0, 0, 0, 0, ZoneOffset.UTC)
        val dayOffset = day.value - DayOfWeek.MONDAY.value
        return monday.plusDays(dayOffset.toLong())
            .plusMinutes(minute.toLong())
    }
}
