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
    fun `JSON backup round trip preserves Quick Block session and saved targets`() {
        val state = FocusAppState(
            quickBlock = QuickBlockSession(
                startedAtEpochMillis = 1_700_000_000_000L,
                startedAtElapsedMillis = 42_000L,
                durationMillis = 25L * 60_000L,
                appPackages = setOf("com.example.reader"),
                websiteDomains = setOf("example.com"),
            ),
            settings = FocusSettings(
                quickBlockApps = setOf("com.example.reader"),
                quickBlockDomains = setOf("example.org"),
                quickBlockMinutes = 45L,
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
