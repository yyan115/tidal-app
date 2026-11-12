package io.github.yyan115.opentune.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppVersionTest {
    @Test fun everyVersionBelowOneIsAPrerelease() {
        assertTrue(AppVersion.isPrerelease("0.7.1"))
        assertTrue(AppVersion.isPrerelease("v0.9.12"))
        assertTrue(AppVersion.isPrerelease("0.7.0-preview"))
    }

    @Test fun aReleaseFromOneOnwardIsNot() {
        assertFalse(AppVersion.isPrerelease("1.0.0"))
        assertFalse(AppVersion.isPrerelease("10.2.3"))
    }

    @Test fun aTestVersionIsAPrereleaseAtAnyNumber() {
        assertTrue(AppVersion.isPrerelease("1.0.0-rc1"))
    }
}
