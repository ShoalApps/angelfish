package io.github.shoalapps.swordfish

import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NavigationTest : TubeTest() {
    @Test fun homeShowsTheBarAndTabs() {
        compose.onNodeWithContentDescription("Search").assertExists()
        listOf("Home", "Shorts", "Subscriptions", "You").forEach { tab(it).assertExists() }
        tab("Home").assertIsSelected()
    }

    @Test fun everyTabOpens() {
        tab("Subscriptions").performClick()
        tab("Subscriptions").assertIsSelected()
        waitFor(hasText("Subscriptions") and !androidx.compose.ui.test.isSelectable())

        tab("You").performClick()
        waitForText("Sign in")
        compose.onNodeWithText("Sign in").assertExists()
        compose.onNodeWithTag("you-list").performScrollToNode(hasText("About Angelfish"))
        compose.onNodeWithText("About Angelfish").assertExists()

        tab("Shorts").performClick()
        tab("Shorts").assertIsSelected()

        tab("Home").performClick()
        tab("Home").assertIsSelected()
        compose.onNodeWithContentDescription("Choose feed").assertExists()
    }

    @Test fun shortsIntroShowsOnceAndCanBeDismissed() {
        AppGraph.store.updateSettings { it.copy(clipsIntroSeen = false) }
        tab("Shorts").performClick()
        waitForText("Short videos have their own app")
        compose.onNodeWithText("Keep watching Shorts here").performClick()
        compose.waitForIdle()
        assertTrue(AppGraph.store.settings.value.clipsIntroSeen)
        assertTrue(gone(hasText("Short videos have their own app")))
    }

    @Test fun searchOpensAndGoesBack() {
        compose.onNodeWithContentDescription("Search").performClick()
        waitForText("Search YouTube")
        compose.onNodeWithContentDescription("Back").performClick()
        waitForDescription("Choose feed")
        tab("Home").assertIsSelected()
    }

    @Test fun aboutPageOpens() {
        tab("You").performClick()
        compose.onNodeWithTag("you-list").performScrollToNode(hasText("About Angelfish"))
        compose.onNodeWithText("About Angelfish").performClick()
        waitForDescription("Back")
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithTag("you-list").performScrollToNode(hasText("About Angelfish"))
        compose.onNodeWithText("About Angelfish").assertExists()
    }

    @Test fun survivesRotationOnEveryTab() {
        for (t in listOf("Home", "Subscriptions", "You", "Shorts")) {
            tab(t).performClick()
            compose.waitForIdle()
            compose.activityRule.scenario.recreate()
            compose.waitForIdle()
            assertTrue("$t lost the bottom bar", !gone(hasText("Home")))
        }
        assertTrue(!gone(hasContentDescription("Search")) || !gone(hasText("Shorts")))
    }
}
