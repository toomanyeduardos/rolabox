package com.eduardoflores.rolabox

import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso
import com.eduardoflores.rolabox.auth.ui.api.SignInKey
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
 * The round trip through Settings (ADR-018, rule 11): the main menu's Settings row pushes the settings
 * list in the display, and its Account row leaves the display for a full screen on the app stack through
 * the generic exit. Back from there, the screen's own or the system's, returns to the device on the
 * settings list, and MENU goes back to the main menu, each with its highlight as it was (rule 7). The
 * stack is the saveable one the app uses, so recreating the activity restores it as it does after the
 * process is killed (ADR-012).
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
                        // The account row opens Sign in while signed out. Its screen is auth's, which :app adds;
                        // here it only has to be a full screen to leave the device for.
                        entry<SignInKey> { BasicText(SIGN_IN_SCREEN) }
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
    fun mainMenuToSettingsToAccount_backAndMenu_restoreEachHighlight() {
        openAccount()

        // A full screen, so back is the app stack's (ADR-018, rule 9), and it stays in the app.
        Espresso.pressBack()

        assertDeviceIsShownOn("Account")
        wheel().pressMenu()
        assertHighlighted("Settings")
    }

    @Test
    fun theRoundTrip_survivesTheActivityBeingRecreated() {
        openAccount()

        scenario.recreate()

        // Sign in is still the top of the app stack, and the device is under it, on the settings list.
        assertAccountIsOpen()
        Espresso.pressBack()
        assertDeviceIsShownOn("Account")
        wheel().pressMenu()
        assertHighlighted("Settings")
    }

    @Test
    fun theSettingsList_isInTheDisplay() {
        highlightSettings()

        wheel().pressCenter()

        // A device screen: the wheel is still drawn, and the first row is highlighted.
        wheel().assertIsDisplayed()
        assertHighlighted("Clicker")
    }

    private fun openAccount() {
        highlightSettings()
        wheel().pressCenter()
        assertHighlighted("Clicker")
        wheel().turn(ACCOUNT_ROW_INDEX)
        assertHighlighted("Account")
        wheel().pressCenter()
        assertAccountIsOpen()
    }

    private fun highlightSettings() {
        assertHighlighted("Music")
        wheel().turn(SETTINGS_ROW_INDEX)
        assertHighlighted("Settings")
    }

    // The account is a full screen, operated by touch: it draws no wheel, and the device is not on top of it.
    private fun assertAccountIsOpen() {
        composeRule.onNodeWithText(SIGN_IN_SCREEN).assertIsDisplayed()
        composeRule.onNodeWithTag(WHEEL_TEST_TAG).assertDoesNotExist()
    }

    private fun assertDeviceIsShownOn(row: String) {
        wheel().assertIsDisplayed()
        assertHighlighted(row)
    }

    private fun wheel() = composeRule.onNodeWithTag(WHEEL_TEST_TAG)

    private fun assertHighlighted(text: String) {
        composeRule.onNode(isSelected() and hasAnyDescendant(hasText(text)), useUnmergedTree = true).assertExists()
    }
}

// Settings is the last row of the main menu, after Music, Podcasts, Audiobooks and Shuffle Songs.
private const val SETTINGS_ROW_INDEX = 4

// Account is the last row of the settings list, after Clicker, Theme, Main Menu and About.
private const val ACCOUNT_ROW_INDEX = 4

private const val SIGN_IN_SCREEN = "Sign in screen"
