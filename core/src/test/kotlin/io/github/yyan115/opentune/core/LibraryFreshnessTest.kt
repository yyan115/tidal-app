package io.github.yyan115.opentune.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryFreshnessTest {
    private val seen = mapOf(
        LibraryList.Favorites to ListProbe(120, "t1"), LibraryList.Albums to ListProbe(14, "a1"),
        LibraryList.Artists to ListProbe(9, "r1"), LibraryList.Playlists to ListProbe(5, "p1@2026-11-20"),
    )

    @Test fun nothingChangedWhenEveryProbeIsTheSame() {
        assertTrue(LibraryFreshness.changed(seen, seen.toMap()).isEmpty())
    }

    @Test fun aListThatGotAnItemChanged() {
        val now = seen + (LibraryList.Favorites to ListProbe(121, "t2"))
        assertEquals(setOf(LibraryList.Favorites), LibraryFreshness.changed(seen, now))
    }

    @Test fun aListThatLostAnItemChangedEvenWhenTheFirstItemStayed() {
        val now = seen + (LibraryList.Albums to ListProbe(13, "a1"))
        assertEquals(setOf(LibraryList.Albums), LibraryFreshness.changed(seen, now))
    }

    @Test fun anItemAddedAndAnItemRemovedChangesTheFirstItem() {
        val now = seen + (LibraryList.Artists to ListProbe(9, "r7"))
        assertEquals(setOf(LibraryList.Artists), LibraryFreshness.changed(seen, now))
    }

    @Test fun aPlaylistChangedInAnotherAppChangesItsProbe() {
        val now = seen + (LibraryList.Playlists to ListProbe(5, "p1@2026-11-26"))
        assertEquals(setOf(LibraryList.Playlists), LibraryFreshness.changed(seen, now))
    }

    @Test fun everyListCountsAsChangedBeforeTheLibraryWasLoaded() {
        assertEquals(LibraryList.entries.toSet(), LibraryFreshness.changed(null, seen))
    }

    @Test fun theAppLooksAgainOnlyAfterTheWaitThatFollowsAFailedLoad() {
        assertTrue(LibraryFreshness.mayLook(null, 1_000L))
        assertTrue(!LibraryFreshness.mayLook(1_000L, 1_000L + LibraryFreshness.RETRY_AFTER_FAILURE_MS - 1))
        assertTrue(LibraryFreshness.mayLook(1_000L, 1_000L + LibraryFreshness.RETRY_AFTER_FAILURE_MS))
    }

    @Test fun everyRequestThatChangesAListNamesThatList() {
        val user = "42"
        assertEquals(LibraryList.Favorites, LibraryFreshness.listChangedBy("users/42/favorites/tracks", user))
        assertEquals(LibraryList.Favorites, LibraryFreshness.listChangedBy("users/42/favorites/tracks/777", user))
        assertEquals(LibraryList.Albums, LibraryFreshness.listChangedBy("users/42/favorites/albums", user))
        assertEquals(LibraryList.Artists, LibraryFreshness.listChangedBy("users/42/favorites/artists/9", user))
        assertEquals(LibraryList.Playlists, LibraryFreshness.listChangedBy("users/42/favorites/playlists", user))
        assertEquals(LibraryList.Playlists, LibraryFreshness.listChangedBy("users/42/playlists", user))
        assertEquals(LibraryList.Playlists, LibraryFreshness.listChangedBy("playlists/abc/items", user))
    }

    @Test fun aRequestOfAnotherListenerOrAboutNoListChangesNoList() {
        assertEquals(null, LibraryFreshness.listChangedBy("users/7/favorites/tracks", "42"))
        assertEquals(null, LibraryFreshness.listChangedBy("tracks/1/lyrics", "42"))
        assertEquals(null, LibraryFreshness.listChangedBy("my-collection/folders", "42"))
    }

    @Test fun theProbeRunsOnceAMinuteAtMost() {
        assertTrue(LibraryFreshness.PROBE_INTERVAL_MS >= 60_000L)
    }
}
