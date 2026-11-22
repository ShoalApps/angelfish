package io.github.shoalapps.swordfish

import android.content.Context
import android.content.ContextWrapper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.shoalapps.swordfish.data.OfflineActions
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class OfflinePersistenceTest {
    @Test fun offlineActionsSurviveReopeningAndStayWithTheirChannel() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val folder = File(context.cacheDir, "outbox-test-" + System.nanoTime()).apply { mkdirs() }
        val isolated = object : ContextWrapper(context) {
            override fun getFilesDir() = folder
        }
        val settings = AppGraph.store.settings.value
        val cookie = AppGraph.store.cookie.value
        val page = AppGraph.store.pageId.value
        try {
            AppGraph.store.updateSettings { it.copy(offlineMode = true) }
            AppGraph.online.value = false
            AppGraph.store.setCookie("SAPISID=instrumentation-offline-only")
            AppGraph.store.setPageId("first-channel")
            val first = OfflineActions(isolated)
            first.submit("rating", "video", "like")
            first.submit("rating", "video", "removelike")
            first.submit("comment", "video", "Saved while offline")
            val originalOwner = first.identity()
            AppGraph.store.setPageId("second-channel")
            first.submit("rating", "video", "like")

            val reopened = OfflineActions(isolated)
            assertEquals(3, reopened.actions.value.size)
            assertEquals("removelike", reopened.actions.value.first().value)
            assertEquals(2, reopened.actions.value.count { it.account == originalOwner })
            assertEquals("Saved while offline", reopened.actions.value[1].value)
            assertNotEquals(originalOwner, reopened.identity())

            reopened.discard(reopened.actions.value[1].id)
            assertEquals(2, OfflineActions(isolated).actions.value.size)
        } finally {
            androidx.work.WorkManager.getInstance(context).cancelUniqueWork("offline-account-sync").result.get()
            AppGraph.store.setCookie(cookie)
            AppGraph.store.setPageId(page)
            AppGraph.store.updateSettings { settings }
            folder.deleteRecursively()
        }
    }
}
