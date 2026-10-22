package com.cyberrick.phoneinspector

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cyberrick.phoneinspector.actions.RemovalBlocker
import com.cyberrick.phoneinspector.actions.RemovalState
import com.cyberrick.phoneinspector.core.model.CheckKind
import com.cyberrick.phoneinspector.rules.Bucket
import com.cyberrick.phoneinspector.rules.Finding
import com.cyberrick.phoneinspector.rules.Labels
import com.cyberrick.phoneinspector.rules.Severity
import com.cyberrick.phoneinspector.ui.DetailScreen
import com.cyberrick.phoneinspector.ui.model.AppCard
import com.cyberrick.phoneinspector.ui.model.AppFacts
import com.cyberrick.phoneinspector.ui.theme.CpiTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The detail screen's two ways out: Keep, and Remove (straight to the dialog, or the two step card first). */
@RunWith(AndroidJUnit4::class)
class DetailScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val card =
        AppCard(
            packageName = "com.example.planted",
            label = "Planted",
            severity = Severity.HIGH,
            cardLabel = Labels.HIDDEN,
            cardText = "No icon, not from a store.",
            findings =
                listOf(
                    Finding(
                        ruleId = "hidden_nonstore_with_power",
                        check = CheckKind.APPS,
                        packageName = "com.example.planted",
                        severity = Severity.HIGH,
                        bucket = Bucket.WORTH_REMOVING,
                        summary = "hidden, not from a store, holds deep access",
                        reason = "r",
                        remediation = "m",
                        evidenceRef = emptyList(),
                        label = Labels.HIDDEN,
                        cardText = "No icon, not from a store.",
                    ),
                ),
            isSystem = false,
        )
    private val facts = AppFacts("microphone", "not watched yet", "at latest 1 Oct 2026", "only when you open it")

    private fun show(
        removal: RemovalState,
        onRemove: () -> Unit = {},
        onKeep: () -> Unit = {},
        onSettings: () -> Unit = {},
    ) {
        compose.setContent {
            CpiTheme {
                DetailScreen(
                    card = card,
                    facts = facts,
                    position = "1 of 1",
                    removal = removal,
                    kept = false,
                    onRemove = onRemove,
                    onOpenSettings = onSettings,
                    onKeep = onKeep,
                    onUnkeep = {},
                    onBack = {},
                )
            }
        }
    }

    @Test
    fun keepTapCallsKeep() {
        var kept = false
        show(RemovalState.Idle, onKeep = { kept = true })

        compose.onNodeWithText("Keep it, I know this app").performScrollTo().performClick()

        check(kept) { "keep was not called" }
    }

    @Test
    fun removeTapAsksForTheDialogAndSaysAndroidWillConfirm() {
        var removed = false
        show(RemovalState.Idle, onRemove = { removed = true })

        compose.onNodeWithText("Remove this app").performScrollTo().performClick()

        check(removed) { "remove was not called" }
        compose
            .onNodeWithText(
                "Android will ask you to confirm. Nothing is removed until you tap Uninstall there.",
            ).performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun adminHolderShowsTheTwoStepCardWithUninstallLockedUntilSettingsIsDone() {
        var settings = false
        show(RemovalState.NeedsSettings("com.example.planted", RemovalBlocker.DEVICE_ADMIN), onSettings = { settings = true })

        compose.onNodeWithText("Two steps to remove it").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Remove this app").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Open Settings").performScrollTo().performClick()

        check(settings) { "settings was not opened" }
    }
}
