package io.github.shoalapps.swordfish

import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

/** Exercise overlapping foreground-service stop/start commands without downloading anything. */
@RunWith(AndroidJUnit4::class)
class ForegroundLifecycleTest {
    @Test fun rapidCancellationAndRestartDoesNotCrash() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val manager = WorkManager.getInstance(context)
        ActivityScenario.launch(ForegroundTestActivity::class.java).use {
            try {
                repeat(20) {
                    val request = OneTimeWorkRequestBuilder<ForegroundLifecycleProbe>().addTag(TAG).build()
                    manager.enqueue(request).result.get(10, TimeUnit.SECONDS)
                    val deadline = SystemClock.elapsedRealtime() + 15_000
                    while (request.id !in ForegroundLifecycleProbe.started) {
                        if (SystemClock.elapsedRealtime() >= deadline) {
                            val state = manager.getWorkInfoById(request.id).get(5, TimeUnit.SECONDS)?.state
                            fail("Foreground worker did not start: $state")
                        }
                        SystemClock.sleep(20)
                    }
                    // Cancellation and the next start deliberately overlap service command delivery.
                    manager.cancelWorkById(request.id).result.get(10, TimeUnit.SECONDS)
                    assertEquals(WorkInfo.State.CANCELLED, manager.getWorkInfoById(request.id).get(5, TimeUnit.SECONDS)?.state)
                }
                // Let Android's foreground-start deadline expire; asynchronous process crashes
                // must fail this test too, rather than arriving after instrumentation has exited.
                SystemClock.sleep(12_000)
                InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            } finally {
                manager.cancelAllWorkByTag(TAG).result.get(10, TimeUnit.SECONDS)
                context.getSystemService(android.app.NotificationManager::class.java).deleteNotificationChannel(ForegroundLifecycleProbe.CHANNEL)
            }
        }
    }
    companion object {
        private const val TAG = "foreground-lifecycle-regression"
    }
}
