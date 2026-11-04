package io.github.shoalapps.swordfish.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UpdaterTest {
    @Test fun readsTheBuildNumberFromReleaseTagsAndUrls() {
        assertEquals(23, Updater.buildOf("build-23"))
        assertEquals(104, Updater.buildOf("https://github.com/ShoalApps/angelfish/releases/tag/build-104"))
    }

    @Test fun ignoresAnythingElse() {
        assertNull(Updater.buildOf("v1.0"))
        assertNull(Updater.buildOf("https://github.com/ShoalApps/angelfish/releases"))
    }
}
