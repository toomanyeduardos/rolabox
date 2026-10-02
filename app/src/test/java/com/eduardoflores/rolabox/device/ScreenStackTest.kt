package com.eduardoflores.rolabox.device

import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import org.junit.Assert.assertEquals
import org.junit.Test

class ScreenStackTest {
    private data object First : NavKey

    private data object Second : NavKey

    private data object Third : NavKey

    private fun stack() = ScreenStack(NavBackStack(First))

    @Test
    fun startsAtTheFirstScreen() {
        val stack = stack()

        assertEquals(listOf<NavKey>(First), stack.keys)
        assertEquals(First, stack.top)
    }

    @Test
    fun push_putsTheScreenOnTop() {
        val stack = stack()

        stack.push(Second)

        assertEquals(listOf<NavKey>(First, Second), stack.keys)
        assertEquals(Second, stack.top)
    }

    @Test
    fun pop_goesBackOneScreen() {
        val stack = stack()
        stack.push(Second)
        stack.push(Third)

        stack.pop()

        assertEquals(listOf<NavKey>(First, Second), stack.keys)
    }

    @Test
    fun pop_onTheFirstScreen_doesNothing() {
        val stack = stack()

        stack.pop()

        assertEquals(listOf<NavKey>(First), stack.keys)
    }

    @Test
    fun popToRoot_keepsOnlyTheFirstScreen() {
        val stack = stack()
        stack.push(Second)
        stack.push(Third)

        stack.popToRoot()

        assertEquals(listOf<NavKey>(First), stack.keys)
    }

    @Test
    fun popToRoot_onTheFirstScreen_doesNothing() {
        val stack = stack()

        stack.popToRoot()

        assertEquals(listOf<NavKey>(First), stack.keys)
    }
}
