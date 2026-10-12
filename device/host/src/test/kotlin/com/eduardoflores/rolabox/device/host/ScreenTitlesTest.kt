package com.eduardoflores.rolabox.device.host

import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import com.eduardoflores.rolabox.common.designsystem.component.DeviceRecord
import com.eduardoflores.rolabox.common.designsystem.wheel.WheelEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The header's title is the one of the screen on top of the stack (ADR-018, rule 19). */
class ScreenTitlesTest {
    private data object First : NavKey

    private data object Second : NavKey

    private data object Third : NavKey

    private val stack = ScreenStack(NavBackStack(First))
    private val inputs = ScreenInputs()
    private val titles = ScreenTitles()
    private val router = WheelEventRouter(stack, inputs) {}

    private val shownTitle get() = titles.titleOf(stack.top)

    /** Pushes a screen that gives its title as it is composed, as [ScreenTitle] does. */
    private fun push(key: NavKey, title: String) {
        stack.push(key)
        ScreenInput(inputs, titles, key).setTitle(title)
    }

    init {
        ScreenInput(inputs, titles, First).setTitle("Rolabox")
    }

    @Test
    fun title_isTheFirstScreens() {
        assertEquals("Rolabox", shownTitle)
    }

    @Test
    fun push_showsTheNewTopScreensTitle() {
        push(Second, "Music")
        assertEquals("Music", shownTitle)

        push(Third, "Artists")
        assertEquals("Artists", shownTitle)
    }

    @Test
    fun menu_showsTheTitleOfTheScreenItReturnsTo() {
        push(Second, "Music")
        push(Third, "Artists")

        router.route(WheelEvent.Menu)
        assertEquals("Music", shownTitle)

        router.route(WheelEvent.Menu)
        assertEquals("Rolabox", shownTitle)
    }

    @Test
    fun holdMenu_showsTheFirstScreensTitle() {
        push(Second, "Music")
        push(Third, "Artists")

        router.route(WheelEvent.HoldMenu)

        assertEquals("Rolabox", shownTitle)
    }

    @Test
    fun aTopScreenThatGaveNoTitle_showsNone_untilItGivesOne() {
        stack.push(Second)
        assertNull(shownTitle)

        ScreenInput(inputs, titles, Second).setTitle("Maren Holt")
        assertEquals("Maren Holt", shownTitle)
    }

    @Test
    fun aTitleSetToNull_showsNone() {
        push(Second, "Maren Holt")

        ScreenInput(inputs, titles, Second).setTitle(null)

        assertNull(shownTitle)
    }

    @Test
    fun aScreenUnderTheTop_keepsItsTitle_andOneThatLeftTheStackDoesNot() {
        push(Second, "Music")
        push(Third, "Artists")
        router.route(WheelEvent.Menu)

        titles.retainOnly(stack.keys)

        assertEquals("Rolabox", titles.titleOf(First))
        assertEquals("Music", titles.titleOf(Second))
        assertNull(titles.titleOf(Third))
    }

    @Test
    fun record_isHiddenWhileNothingIsLoaded() {
        assertEquals(DeviceRecord.Hidden, recordOf(isItemLoaded = false, isItemPlaying = false))
        assertEquals(DeviceRecord.Hidden, recordOf(isItemLoaded = false, isItemPlaying = true))
    }

    @Test
    fun record_spinsWhilePlaying_andIsStillWhilePaused() {
        assertEquals(DeviceRecord.Spinning, recordOf(isItemLoaded = true, isItemPlaying = true))
        assertEquals(DeviceRecord.Still, recordOf(isItemLoaded = true, isItemPlaying = false))
    }
}
