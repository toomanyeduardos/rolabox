package com.eduardoflores.rolabox.device.host

import androidx.compose.runtime.saveable.SaverScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceTypingTest {
    private val saverScope = SaverScope { true }

    @Test
    fun start_startsTyping_andEnd_endsIt() {
        val typing = DeviceTyping(false)

        typing.start()
        assertTrue(typing.isTyping)

        typing.end()
        assertFalse(typing.isTyping)
    }

    @Test
    fun whetherItIsTyping_isSavedAndRestored() {
        listOf(true, false).forEach { isTyping ->
            val saved = with(DeviceTyping.Saver) { saverScope.save(DeviceTyping(isTyping)) }

            assertEquals(isTyping, DeviceTyping.Saver.restore(saved!!)?.isTyping)
        }
    }

    @Test
    fun theDevice_movesUpOnlyAsFarAsTheDisplayIsUnderTheKeyboard() {
        // No keyboard: its top is the window's bottom.
        assertEquals(0f, keyboardLift(displayBottom = 1500f, keyboardTop = 2400), 0f)
        // A keyboard under the display, over the wheel only.
        assertEquals(0f, keyboardLift(displayBottom = 1500f, keyboardTop = 1600), 0f)
        // A tall keyboard that would cover the display's bottom.
        assertEquals(300f, keyboardLift(displayBottom = 1500f, keyboardTop = 1200), 0f)
    }
}
