package com.eduardoflores.rolabox.device.host

import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pressKey
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import com.eduardoflores.rolabox.common.designsystem.component.DEVICE_DISPLAY_TEST_TAG
import com.eduardoflores.rolabox.common.designsystem.component.DeviceList
import com.eduardoflores.rolabox.common.designsystem.component.WHEEL_TEST_TAG
import com.eduardoflores.rolabox.common.designsystem.theme.RolaboxTheme
import com.eduardoflores.rolabox.common.designsystem.wheel.WheelEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Typing on the device (ADR-032) in a real window: the keyboard shows over the wheel, the display stays above
 * it, the wheel sends nothing while typing, and it comes back when the keyboard goes.
 */
class TypingFieldTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private data object Search : NavKey

    private var centers = 0
    private var typingEnds = 0
    private var actions = 0

    @Before
    fun edgeToEdge() {
        // As MainActivity is: edge to edge, with adjustResize, so the keyboard is an inset the host reads.
        rule.runOnUiThread {
            rule.activity.enableEdgeToEdge()
            rule.activity.window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        }
        rule.setContent {
            RolaboxTheme {
                val stack = remember { ScreenStack(NavBackStack(Search)) }
                DeviceHost(
                    stack = stack,
                    isItemLoaded = false,
                    isItemPlaying = false,
                    clicks = false,
                    entryProvider = { key -> NavEntry(key) { SearchScreen() } },
                    onPlaybackEvent = {},
                )
            }
        }
    }

    /** A screen with a field and no result, which opens with the keyboard up, as Search does. */
    @Composable
    private fun SearchScreen() {
        val typing = rememberDeviceTyping(typeWhenShown = true)
        val highlight = rememberListHighlight()
        ScreenTitle("Search")
        HandleWheelEvents { event ->
            if (event == WheelEvent.Center) {
                centers++
                if (highlight.isOnField) typing.start()
            }
        }
        HandleMenuWithField(highlight)
        DeviceList(rows = emptyList(), highlightedIndex = highlight.rowIndex(typing)) {
            TypingField(
                typing = typing,
                state = rememberTextFieldState(),
                hint = "Shows, creators, episodes",
                shortHint = "Search",
                highlighted = highlight.isOnField,
                onTypingEnd = {
                    typingEnds++
                    highlight.highlightAfterTyping(emptyList())
                },
                onAction = { actions++ },
            )
        }
    }

    private fun imeInsets(): WindowInsetsCompat? = ViewCompat.getRootWindowInsets(rule.activity.window.decorView)

    private fun isKeyboardShown(): Boolean = imeInsets()?.isVisible(WindowInsetsCompat.Type.ime()) == true

    private fun tapWheelCenter() {
        rule.onNodeWithTag(WHEEL_TEST_TAG).performTouchInput { click(center) }
        rule.waitForIdle()
    }

    @Test
    fun theKeyboardShows_theDisplayStaysAboveIt_andTheWheelComesBackWhenItGoes() {
        val displayBottomBefore = rule.onNodeWithTag(DEVICE_DISPLAY_TEST_TAG).fetchSemanticsNode().boundsInRoot.bottom
        // An emulator with a hardware keyboard shows none: the rest of the test needs one.
        val shown = runCatching { rule.waitUntil(KEYBOARD_TIMEOUT_MILLIS) { isKeyboardShown() } }.isSuccess
        assumeTrue("The soft keyboard didn't show; turn off the hardware keyboard", shown)
        rule.waitForIdle()

        val root = rule.activity.window.decorView
        val keyboardTop = root.height - imeInsets()!!.getInsets(WindowInsetsCompat.Type.ime()).bottom
        val displayBottom = rule.onNodeWithTag(DEVICE_DISPLAY_TEST_TAG).fetchSemanticsNode().boundsInRoot.bottom
        assertTrue(
            "The display's bottom, $displayBottom, is under the keyboard's top, $keyboardTop",
            displayBottom <= keyboardTop + 1,
        )

        rule.runOnUiThread {
            WindowCompat.getInsetsController(rule.activity.window, root).hide(WindowInsetsCompat.Type.ime())
        }
        rule.waitUntil(KEYBOARD_TIMEOUT_MILLIS) { !isKeyboardShown() && typingEnds == 1 }
        rule.waitForIdle()
        assertEquals(
            displayBottomBefore,
            rule.onNodeWithTag(DEVICE_DISPLAY_TEST_TAG).fetchSemanticsNode().boundsInRoot.bottom,
            1f,
        )

        tapWheelCenter()

        assertEquals("The wheel is back, on the field", 1, centers)
    }

    @Test
    fun whileTyping_aTouchOnTheWheel_onlyEndsTyping() {
        tapWheelCenter()

        assertEquals(0, centers)
        assertEquals(1, typingEnds)

        tapWheelCenter()

        assertEquals("The next touch is the wheel's again", 1, centers)
    }

    @Test
    fun escOnAHardwareKeyboard_endsTyping() {
        rule.onNode(hasSetTextAction()).performKeyInput { pressKey(Key.Escape) }
        rule.waitForIdle()

        assertEquals(1, typingEnds)
        assertEquals(0, actions)
    }

    @Test
    fun enterOnAHardwareKeyboard_callsTheActionThenEndsTyping() {
        rule.onNode(hasSetTextAction()).performKeyInput { pressKey(Key.Enter) }
        rule.waitForIdle()

        assertEquals(1, actions)
        assertEquals(1, typingEnds)
    }

    private companion object {
        const val KEYBOARD_TIMEOUT_MILLIS = 5_000L
    }
}
