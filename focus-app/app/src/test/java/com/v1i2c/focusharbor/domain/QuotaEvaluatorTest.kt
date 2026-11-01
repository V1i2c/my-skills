package com.v1i2c.focusharbor.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QuotaEvaluatorTest {
    @Test
    fun `time quota blocks at boundary but not before`() {
        val quota = UsageQuota(maximumMinutes = 30)
        assertFalse(QuotaEvaluator.isExceeded(quota, UsageRecord("day", "app", foregroundMillis = 29 * 60_000L)))
        assertTrue(QuotaEvaluator.isExceeded(quota, UsageRecord("day", "app", foregroundMillis = 30 * 60_000L)))
    }

    @Test
    fun `launch quota blocks at boundary but not before`() {
        val quota = UsageQuota(maximumLaunches = 5)
        assertFalse(QuotaEvaluator.isExceeded(quota, UsageRecord("day", "app", launches = 4)))
        assertTrue(QuotaEvaluator.isExceeded(quota, UsageRecord("day", "app", launches = 5)))
    }

    @Test
    fun `missing usage does not block`() {
        assertFalse(QuotaEvaluator.isExceeded(UsageQuota(maximumMinutes = 1), null))
    }
}
