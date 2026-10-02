package com.eduardoflores.rolabox.core.device

import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import com.eduardoflores.rolabox.core.designsystem.wheel.HoldState
import com.eduardoflores.rolabox.core.designsystem.wheel.WheelEvent
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
    fun turnCenterAndMenu_neverReachThePlaybackHandler() {
        stack.push(Second)

        router.route(WheelEvent.Turn(1))
        router.route(WheelEvent.Center)
        router.route(WheelEvent.Menu)
        router.route(WheelEvent.HoldMenu)

        assertEquals(emptyList<WheelEvent>(), playbackReceived)
    }
}
