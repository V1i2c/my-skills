package com.v1i2c.focusharbor.data

import com.v1i2c.focusharbor.domain.FocusAppState
import org.junit.Assert.assertEquals
import org.junit.Test

class StateOperationsTest {
    @Test
    fun `focus time preserves partial minutes across usage flushes`() {
        val first = StateOperations.recordUsage(
            state = FocusAppState(),
            packageName = "com.example.reader",
            periodKey = "2026-01-05",
            foregroundDeltaMillis = 40_000L,
            recordFocus = true,
            dateKey = "2026-01-05",
        )
        val second = StateOperations.recordUsage(
            state = first,
            packageName = "com.example.reader",
            periodKey = "2026-01-05",
            foregroundDeltaMillis = 40_000L,
            recordFocus = true,
            dateKey = "2026-01-05",
        )

        assertEquals(1L, second.dailyStats.single().focusMinutes)
        assertEquals(20_000L, second.dailyStats.single().focusMillisRemainder)
    }
}
