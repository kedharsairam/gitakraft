package com.gitakraft.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Test

class UpdatesTest {
    @Test
    fun compareVersionsNumeric() {
        assertEquals(0, Updates.compareVersions("1.0", "1.0"))
        assertEquals(0, Updates.compareVersions("v0.1.0", "0.1.0"))
        assertEquals(1, Updates.compareVersions("v0.2.0", "0.1.0").let { if (it > 0) 1 else it })
        assertEquals(-1, Updates.compareVersions("0.1.0", "0.2.0").let { if (it < 0) -1 else it })
        // 10 > 9 numerically (never lexicographically).
        assertEquals(1, Updates.compareVersions("1.2.10", "1.2.9").let { if (it > 0) 1 else it })
    }

    @Test
    fun parseReleaseMinimal() {
        val r = Updates.parseRelease(
            """{"tag_name":"v0.2.0","body":"Notes","assets":[]}""",
        )
        assertEquals("v0.2.0", r.tag)
        assertEquals("Notes", r.notes)
        // No APK asset -> not downloadable -> null, not a crash.
        assertNull(Updates.toAvailable(r, "0.1.0"))
    }

    @Test
    fun toAvailableNeedsNewerTagAndApk() {
        val base = Updates.Release(
            tag = "v0.2.0",
            notes = "n",
            assets = listOf(
                Updates.ReleaseAsset("app-release.apk", "https://github.com/x/y.apk", 1_000_000),
            ),
        )
        val got = Updates.toAvailable(base, "0.1.0")
        assertNotNull(got)
        assertEquals("0.2.0", got!!.version)
        // Same version -> nothing new.
        assertNull(Updates.toAvailable(base, "0.2.0"))
        // Older tag -> nothing new.
        assertNull(Updates.toAvailable(base, "0.3.0"))
    }

    @Test
    fun formatBytesUnits() {
        assertEquals("512 B", Updates.formatBytes(512))
        assertEquals("2.0 KB", Updates.formatBytes(2048))
        assertEquals("20.5 MB", Updates.formatBytes(21_504_000))
    }
}
