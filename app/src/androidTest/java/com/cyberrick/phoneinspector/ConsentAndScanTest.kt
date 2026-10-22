package com.cyberrick.phoneinspector

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
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

    private fun waitForResult(timeoutMillis: Long = 60_000) {
        compose.waitUntil(timeoutMillis) { compose.onAllNodes(hasText("Scan again")).fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun firstScanShowsTheSheetOnceThenReachesAResult() {
        TestShell.resetConsentFlag()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText("Scan now").assertIsDisplayed()

        compose.onNodeWithText("Scan now").performClick()
        compose.onNodeWithText("Before the first scan").assertIsDisplayed()
        compose.onNodeWithText("Continue and scan").performScrollTo().performClick()

        waitForResult()
        compose.onNodeWithText("See the full report").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun fullReportIsBehindTheResultAndShareIsThere() {
        TestShell.resetConsentFlag()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText("Scan now").performClick()
        compose.onNodeWithText("Continue and scan").performScrollTo().performClick()
        waitForResult()

        compose.onNodeWithText("See the full report").performScrollTo().performClick()

        compose.onNodeWithText("Share report").assertIsDisplayed()
        compose.waitUntil(5_000) { compose.onAllNodes(hasText("VERDICT: ", substring = true)).fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun deniedUsageAccessCapsTheScoreAndOffersThePermission() {
        TestShell.grantUsageAccess(false)
        TestShell.resetConsentFlag()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText("Scan now").performClick()
        compose.onNodeWithText("Continue and scan").performScrollTo().performClick()
        waitForResult()

        compose.onAllNodes(hasText("could not run", substring = true)).fetchSemanticsNodes().let {
            check(it.isNotEmpty()) { "result should say a check could not run" }
        }
        compose.onNodeWithText("Grant usage access").performScrollTo().assertIsDisplayed()
        TestShell.grantUsageAccess(true)
    }
}
