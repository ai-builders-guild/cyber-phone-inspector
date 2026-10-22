package com.cyberrick.phoneinspector

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cyberrick.phoneinspector.ui.MainActivity
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ConsentAndScanTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Before
    fun grant() {
        TestShell.grantUsageAccess(true)
        TestShell.run("pm grant ${TestShell.context.packageName} android.permission.POST_NOTIFICATIONS")
    }

    @Test
    fun firstRunShowsConsentThenHome() {
        // The activity already started with whatever flag was stored; reset and relaunch within the test.
        TestShell.resetConsentFlag()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText("Before the first scan").assertIsDisplayed()

        compose.onNodeWithText("Continue").performClick()

        compose.onNodeWithText("Start scan").assertIsDisplayed()
    }

    @Test
    fun fullScanReachesAVerdictWithinTwentySeconds() {
        TestShell.resetConsentFlag()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText("Continue").performClick()

        compose.onNodeWithText("Start scan").performClick()

        compose.waitUntil(
            timeoutMillis = 20_000,
        ) { compose.onAllNodes(hasText("VERDICT: ", substring = true)).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Share report").assertIsDisplayed()
    }

    @Test
    fun deniedUsageAccessIsReportedNotChecked() {
        TestShell.grantUsageAccess(false)
        TestShell.resetConsentFlag()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText("Continue").performClick()
        compose.onNodeWithText("Open usage access settings").assertIsDisplayed()

        compose.onNodeWithText("Start scan").performClick()

        compose.waitUntil(
            timeoutMillis = 20_000,
        ) { compose.onAllNodes(hasText("VERDICT: ", substring = true)).fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodes(hasText("NOT CHECKED", substring = true)).fetchSemanticsNodes().let {
            check(it.isNotEmpty()) { "report should say NOT CHECKED" }
        }
        TestShell.grantUsageAccess(true)
    }
}
