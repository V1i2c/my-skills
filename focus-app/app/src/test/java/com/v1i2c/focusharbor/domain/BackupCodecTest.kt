package com.v1i2c.focusharbor.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class BackupCodecTest {
    @Test
    fun `json backup round trip keeps profiles and rules`() {
        val state = FocusAppState(
            profiles = listOf(
                FocusProfile(
                    id = "study",
                    name = "Study",
                    appPackages = setOf("com.example.reader"),
                    websiteDomains = setOf("reddit.com"),
                    schedules = listOf(ScheduleRule(setOf(1, 2), 540, 720)),
                ),
            ),
        )

        assertEquals(state, BackupCodec.decode(BackupCodec.encode(state)))
    }

    @Test
    fun `CSV import normalizes URLs and removes duplicates`() {
        val domains = WebsiteCsvCodec.parseDomains("domain\nHTTPS://WWW.Reddit.com/r/kotlin\nreddit.com\nexample.org")
        assertEquals(setOf("reddit.com", "example.org"), domains)
    }

    @Test
    fun `CSV import rejects unclosed quotes`() {
        assertThrows(IllegalArgumentException::class.java) {
            WebsiteCsvCodec.parseDomains("\"reddit.com")
        }
    }
}
