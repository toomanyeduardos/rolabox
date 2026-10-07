package com.eduardoflores.rolabox

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import androidx.test.core.app.ActivityScenario
import com.eduardoflores.rolabox.common.designsystem.component.WHEEL_TEST_TAG
import com.eduardoflores.rolabox.common.designsystem.theme.RolaboxTheme
import com.eduardoflores.rolabox.device.ui.api.DeviceKey
import com.eduardoflores.rolabox.device.ui.api.DeviceUiEntries
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import javax.inject.Inject
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * The round trip to Settings (ADR-018, rule 11): the main menu's Settings row opens a full screen on
 * the app stack through the generic exit, and back returns to the device with its screen stack and
 * highlight as they were. The stack is the saveable one the app uses, so recreating the activity
 * restores it as it does after the process is killed (ADR-012).
 */
@HiltAndroidTest
class SettingsNavigationTest {
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createEmptyComposeRule()

    private lateinit var scenario: ActivityScenario<HiltTestActivity>

    @Inject lateinit var deviceUiEntries: DeviceUiEntries

    @Before
    fun setUp() {
        hiltRule.inject()
        HiltTestActivity.content = {
            RolaboxTheme {
                val backStack = rememberNavBackStack(DeviceKey)
                NavDisplay(
                    backStack = backStack,
                    onBack = { if (backStack.size > 1) backStack.removeAt(backStack.lastIndex) },
                    entryDecorators = listOf(
                        rememberSaveableStateHolderNavEntryDecorator(),
                        rememberViewModelStoreNavEntryDecorator(),
                    ),
                    entryProvider = entryProvider {
                        deviceUiEntries.appStackEntries(
                            scope = this,
                            // What :app does with the generic exit.
                            onOpenFullScreen = { backStack.add(it) },
                            onBack = { if (backStack.size > 1) backStack.removeAt(backStack.lastIndex) },
                        )
                    },
                )
            }
        }
        scenario = ActivityScenario.launch(HiltTestActivity::class.java)
    }

    @After
    fun tearDown() {
        scenario.close()
    }

    @Test
    fun mainMenuToSettingsAndBack_theHighlightIsUnchanged() {
        highlightSettings()

        wheel().pressCenter()

        assertSettingsIsOpen()
        composeRule.onNodeWithContentDescription("Back").performClick()

        assertDeviceIsShownOnSettings()
    }

    @Test
    fun theRoundTrip_survivesTheActivityBeingRecreated() {
        highlightSettings()
        wheel().pressCenter()

        scenario.recreate()

        // Settings is still the top of the app stack, and the device is under it.
        assertSettingsIsOpen()
        composeRule.onNodeWithContentDescription("Back").performClick()
        assertDeviceIsShownOnSettings()
    }

    @Test
    fun theHighlight_survivesTheActivityBeingRecreatedWhileInSettings() {
        highlightSettings()
        wheel().pressCenter()
        scenario.recreate()
        composeRule.onNodeWithContentDescription("Back").performClick()

        scenario.recreate()

        // The device's own saved state came back with the stack, so Settings is still the highlighted row.
        assertDeviceIsShownOnSettings()
    }

    private fun highlightSettings() {
        assertHighlighted("Music")
        wheel().turn(SETTINGS_ROW_INDEX)
        assertHighlighted("Settings")
    }

    // Settings is a full screen, operated by touch: it draws no wheel, and the device is not on top of it.
    private fun assertSettingsIsOpen() {
        composeRule.onNodeWithText("Theme").assertIsDisplayed()
        composeRule.onNodeWithTag(WHEEL_TEST_TAG).assertDoesNotExist()
    }

    private fun assertDeviceIsShownOnSettings() {
        wheel().assertIsDisplayed()
        assertHighlighted("Settings")
    }

    private fun wheel() = composeRule.onNodeWithTag(WHEEL_TEST_TAG)

    private fun assertHighlighted(text: String) {
        composeRule.onNode(isSelected() and hasAnyDescendant(hasText(text)), useUnmergedTree = true).assertExists()
    }
}

// Settings is the last row of the main menu, after Music, Podcasts, Audiobooks and Shuffle Songs.
private const val SETTINGS_ROW_INDEX = 4
