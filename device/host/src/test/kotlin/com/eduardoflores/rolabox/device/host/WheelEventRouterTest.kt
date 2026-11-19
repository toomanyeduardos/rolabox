package com.eduardoflores.rolabox.device.host

import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import com.eduardoflores.rolabox.common.designsystem.wheel.HoldState
import com.eduardoflores.rolabox.common.designsystem.wheel.WheelEvent
import org.junit.Assert.assertEquals
import org.junit.Test

class WheelEventRouterTest {
    private data object First : NavKey

    private data object Second : NavKey

    private val stack = ScreenStack(NavBackStack(First))
    private val inputs = ScreenInputs()
    private val playbackReceived = mutableListOf<WheelEvent>()
    private val router = WheelEventRouter(stack, inputs) { playbackReceived += it }
    private val firstReceived = mutableListOf<WheelEvent>()
    private val secondReceived = mutableListOf<WheelEvent>()

    init {
        inputs.register(First) { firstReceived += it }
        inputs.register(Second) { secondReceived += it }
    }

    private val playbackEvents = listOf(
        WheelEvent.Previous,
        WheelEvent.Next,
        WheelEvent.PlayPause,
        WheelEvent.HoldPrevious(HoldState.Held),
        WheelEvent.HoldPrevious(HoldState.Released),
        WheelEvent.HoldNext(HoldState.Held),
        WheelEvent.HoldNext(HoldState.Released),
    )

    @Test
    fun turnAndCenter_goToTheTopScreenOnly() {
        stack.push(Second)

        router.route(WheelEvent.Turn(3))
        router.route(WheelEvent.Center)

        assertEquals(listOf(WheelEvent.Turn(3), WheelEvent.Center), secondReceived)
        assertEquals(emptyList<WheelEvent>(), firstReceived)
    }

    @Test
    fun turnAndCenter_followTheTopScreenBack() {
        stack.push(Second)
        stack.pop()

        router.route(WheelEvent.Turn(-1))

        assertEquals(listOf<WheelEvent>(WheelEvent.Turn(-1)), firstReceived)
        assertEquals(emptyList<WheelEvent>(), secondReceived)
    }

    @Test
    fun turn_withNoScreenRegistered_isDropped() {
        inputs.unregister(First)

        router.route(WheelEvent.Turn(1))

        assertEquals(emptyList<WheelEvent>(), firstReceived)
    }

    @Test
    fun menu_goesBackOneScreen_andNoScreenSeesIt() {
        stack.push(Second)

        router.route(WheelEvent.Menu)

        assertEquals(listOf<NavKey>(First), stack.keys)
        assertEquals(emptyList<WheelEvent>(), firstReceived + secondReceived)
    }

    @Test
    fun menu_onTheFirstScreen_doesNothing() {
        router.route(WheelEvent.Menu)

        assertEquals(listOf<NavKey>(First), stack.keys)
        assertEquals(emptyList<WheelEvent>(), firstReceived)
    }

    @Test
    fun menu_goesToTheTopScreenThatAskedForIt_andTheStackStays() {
        stack.push(Second)
        var menus = 0
        inputs.registerMenu(Second) { menus++ }

        router.route(WheelEvent.Menu)

        assertEquals(1, menus)
        assertEquals(listOf<NavKey>(First, Second), stack.keys)
        assertEquals(emptyList<WheelEvent>(), firstReceived + secondReceived)
    }

    @Test
    fun menu_goesBackAgain_onceTheScreenStopsAskingForIt() {
        stack.push(Second)
        inputs.registerMenu(Second) {}
        inputs.unregisterMenu(Second)

        router.route(WheelEvent.Menu)

        assertEquals(listOf<NavKey>(First), stack.keys)
    }

    @Test
    fun menu_neverGoesToAScreenBelowTheTop() {
        var menus = 0
        inputs.registerMenu(First) { menus++ }
        stack.push(Second)

        router.route(WheelEvent.Menu)

        assertEquals(0, menus)
        assertEquals(listOf<NavKey>(First), stack.keys)
    }

    @Test
    fun holdMenu_goesToTheFirstScreen_evenWhenTheTopScreenAskedForMenu() {
        stack.push(Second)
        var menus = 0
        inputs.registerMenu(Second) { menus++ }

        router.route(WheelEvent.HoldMenu)

        assertEquals(0, menus)
        assertEquals(listOf<NavKey>(First), stack.keys)
    }

    @Test
    fun holdMenu_goesToTheFirstScreen() {
        stack.push(Second)
        stack.push(First)

        router.route(WheelEvent.HoldMenu)

        assertEquals(listOf<NavKey>(First), stack.keys)
    }

    @Test
    fun playbackButtons_goToTheirHandler_andNoScreenSeesThem() {
        stack.push(Second)

        playbackEvents.forEach(router::route)

        assertEquals(playbackEvents, playbackReceived)
        assertEquals(listOf<NavKey>(First, Second), stack.keys)
        assertEquals(emptyList<WheelEvent>(), firstReceived + secondReceived)
    }

    @Test
    fun playPause_goesToTheTopScreenThatAskedForIt_andNotToThePlaybackHandler() {
        stack.push(Second)
        var playPauses = 0
        inputs.registerPlayPause(Second) { playPauses++ }

        router.route(WheelEvent.PlayPause)

        assertEquals(1, playPauses)
        assertEquals(emptyList<WheelEvent>(), playbackReceived)
        assertEquals(emptyList<WheelEvent>(), firstReceived + secondReceived)
        assertEquals(listOf<NavKey>(First, Second), stack.keys)
    }

    @Test
    fun playPause_goesToThePlaybackHandlerAgain_onceTheScreenStopsAskingForIt() {
        stack.push(Second)
        inputs.registerPlayPause(Second) {}
        inputs.unregisterPlayPause(Second)

        router.route(WheelEvent.PlayPause)

        assertEquals(listOf<WheelEvent>(WheelEvent.PlayPause), playbackReceived)
    }

    @Test
    fun playPause_neverGoesToAScreenBelowTheTop() {
        var playPauses = 0
        inputs.registerPlayPause(First) { playPauses++ }
        stack.push(Second)

        router.route(WheelEvent.PlayPause)

        assertEquals(0, playPauses)
        assertEquals(listOf<WheelEvent>(WheelEvent.PlayPause), playbackReceived)
    }

    @Test
    fun theOtherPlaybackButtons_neverGoToAScreenThatAskedForPlayPause() {
        stack.push(Second)
        var playPauses = 0
        inputs.registerPlayPause(Second) { playPauses++ }
        val others = playbackEvents - WheelEvent.PlayPause

        others.forEach(router::route)

        assertEquals(0, playPauses)
        assertEquals(others, playbackReceived)
    }

    @Test
    fun playbackButtons_tellTheTopScreenThatAskedFirst_andStillGoToTheirHandler() {
        stack.push(Second)
        val order = mutableListOf<String>()
        val router = WheelEventRouter(stack, inputs) { order += "handler" }
        inputs.registerPlaybackButtons(Second) { order += "screen" }

        playbackEvents.forEach(router::route)

        assertEquals(playbackEvents.flatMap { listOf("screen", "handler") }, order)
        assertEquals(emptyList<WheelEvent>(), firstReceived + secondReceived)
        assertEquals(listOf<NavKey>(First, Second), stack.keys)
    }

    @Test
    fun playbackButtons_stopTellingTheScreen_onceItStopsAsking() {
        stack.push(Second)
        var told = 0
        inputs.registerPlaybackButtons(Second) { told++ }
        inputs.unregisterPlaybackButtons(Second)

        playbackEvents.forEach(router::route)

        assertEquals(0, told)
        assertEquals(playbackEvents, playbackReceived)
    }

    @Test
    fun playbackButtons_neverTellAScreenBelowTheTop() {
        var told = 0
        inputs.registerPlaybackButtons(First) { told++ }
        stack.push(Second)

        playbackEvents.forEach(router::route)

        assertEquals(0, told)
        assertEquals(playbackEvents, playbackReceived)
    }

    @Test
    fun playPause_thatTheTopScreenTook_isNotToldAsAPlaybackButton() {
        stack.push(Second)
        var told = 0
        inputs.registerPlayPause(Second) {}
        inputs.registerPlaybackButtons(Second) { told++ }

        router.route(WheelEvent.PlayPause)

        assertEquals(0, told)
        assertEquals(emptyList<WheelEvent>(), playbackReceived)
    }

    @Test
    fun turnCenterAndMenu_neverTellOfAPlaybackButton() {
        stack.push(Second)
        var told = 0
        inputs.registerPlaybackButtons(Second) { told++ }

        router.route(WheelEvent.Turn(1))
        router.route(WheelEvent.Center)
        router.route(WheelEvent.Menu)
        router.route(WheelEvent.HoldMenu)

        assertEquals(0, told)
    }

    @Test
    fun turnCenterAndMenu_neverReachThePlaybackHandler() {
        stack.push(Second)

        router.route(WheelEvent.Turn(1))
        router.route(WheelEvent.Center)
        router.route(WheelEvent.Menu)
        router.route(WheelEvent.HoldMenu)

        assertEquals(emptyList<WheelEvent>(), playbackReceived)
    }

    private val everyEvent = listOf(WheelEvent.Turn(1), WheelEvent.Center, WheelEvent.Menu, WheelEvent.HoldMenu) +
        playbackEvents

    @Test
    fun whileTyping_theWheelSendsNothing() {
        stack.push(Second)
        var menus = 0
        var playPauses = 0
        var told = 0
        inputs.registerMenu(Second) { menus++ }
        inputs.registerPlayPause(Second) { playPauses++ }
        inputs.registerPlaybackButtons(Second) { told++ }
        inputs.registerTyping(Second) {}

        everyEvent.forEach(router::route)

        assertEquals(emptyList<WheelEvent>(), firstReceived + secondReceived)
        assertEquals(emptyList<WheelEvent>(), playbackReceived)
        assertEquals(listOf(0, 0, 0), listOf(menus, playPauses, told))
        assertEquals(listOf<NavKey>(First, Second), stack.keys)
    }

    @Test
    fun onceTypingEnds_theWheelSendsAgain() {
        inputs.registerTyping(First) {}
        inputs.unregisterTyping(First)

        router.route(WheelEvent.Turn(1))
        router.route(WheelEvent.Next)

        assertEquals(listOf<WheelEvent>(WheelEvent.Turn(1)), firstReceived)
        assertEquals(listOf<WheelEvent>(WheelEvent.Next), playbackReceived)
    }

    @Test
    fun typingOnAScreenBelowTheTop_doesntStopTheWheel() {
        inputs.registerTyping(First) {}
        stack.push(Second)

        router.route(WheelEvent.Center)

        assertEquals(listOf<WheelEvent>(WheelEvent.Center), secondReceived)
    }

    @Test
    fun endTyping_endsTheTypingOfThatScreenOnly() {
        val ended = mutableListOf<NavKey>()
        inputs.registerTyping(First) { ended += First }
        inputs.registerTyping(Second) { ended += Second }

        inputs.endTyping(Second)

        assertEquals(listOf<NavKey>(Second), ended)
    }

    @Test
    fun menuOnARow_highlightsTheField_andMenuOnTheField_goesBack() {
        stack.push(Second)
        val highlight = ListHighlight(2)

        // What HandleMenuWithField registers, while the screen takes MENU.
        fun register() {
            if (screenTakesMenu(highlight)) {
                inputs.registerMenu(Second) {
                    highlight.highlightField()
                }
            } else {
                inputs.unregisterMenu(Second)
            }
        }

        register()
        router.route(WheelEvent.Menu)

        assertEquals(true, highlight.isOnField)
        assertEquals(listOf<NavKey>(First, Second), stack.keys)

        register()
        router.route(WheelEvent.Menu)

        assertEquals(listOf<NavKey>(First), stack.keys)
    }
}
