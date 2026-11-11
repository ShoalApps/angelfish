package io.github.shoalapps.swordfish

import android.Manifest
import android.os.Build
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.rule.GrantPermissionRule
import io.github.shoalapps.swordfish.data.Settings
import io.github.shoalapps.swordfish.player.PlayerHolder
import io.github.shoalapps.swordfish.ui.StartupState
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.rules.RuleChain
import org.junit.rules.TestRule

/** Shared setup: fresh settings, notifications allowed, and helpers for waiting on the network. */
abstract class TubeTest {
    private val permissions: TestRule =
        if (Build.VERSION.SDK_INT >= 33) GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS)
        else GrantPermissionRule.grant()

    /** Resets saved state before the activity starts, so every test begins on a clean Home. */
    private val reset: TestRule = TestRule { base, _ ->
        object : org.junit.runners.model.Statement() {
            override fun evaluate() {
                val store = AppGraph.store
                // Fresh settings, but past the one-time Clips intro, which has its own test.
                store.updateSettings { Settings(clipsIntroSeen = true) }
                store.clearHistory()
                store.searches.value.forEach(store::removeSearch)
                base.evaluate()
            }
        }
    }

    val compose: AndroidComposeTestRule<ActivityScenarioRule<MainActivity>, MainActivity> =
        createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(permissions).around(reset).around(compose)

    @Before
    fun waitForIntro() {
        // The launch animation steps aside once Home has loaded, or after a few seconds at most.
        // On CI YouTube may refuse to answer at all; the intro gives up after a few seconds anyway.
        runCatching { compose.waitUntil(15_000) { StartupState.homeReady } }
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
        if (compose.onAllNodesWithText("Done").fetchSemanticsNodes().isNotEmpty()) {
            compose.onNodeWithText("Done").performClick()
            compose.waitForIdle()
        }
    }

    @After
    fun stopPlayback() {
        compose.runOnUiThread {
            PlayerHolder.player.stop()
            PlayerHolder.player.clearMediaItems()
        }
    }

    /**
     * Presses Back through the activity, like Espresso.pressBack() but without waiting for window
     * focus, which a slow emulator can briefly hand to a system dialog or a PiP window.
     */
    fun back() {
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
    }

    fun tab(label: String): SemanticsNodeInteraction = compose.onNode(hasText(label) and isSelectable())

    fun waitFor(matcher: SemanticsMatcher, timeoutMs: Long = 10_000) {
        compose.waitUntil(timeoutMs) { count(matcher) > 0 }
    }

    fun waitForText(text: String, timeoutMs: Long = 10_000, substring: Boolean = false) =
        waitFor(hasText(text, substring = substring), timeoutMs)

    fun waitForDescription(desc: String, timeoutMs: Long = 10_000) = waitFor(hasContentDescription(desc), timeoutMs)

    fun gone(matcher: SemanticsMatcher) = count(matcher) == 0

    /** Matching nodes on screen; 0 while the activity is briefly between states (e.g. handling a new intent). */
    private fun count(matcher: SemanticsMatcher): Int =
        try { compose.onAllNodes(matcher).fetchSemanticsNodes().size } catch (e: IllegalStateException) { 0 }
}
