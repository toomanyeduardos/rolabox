package com.eduardoflores.rolabox.core.data.util

import arrow.core.left
import arrow.core.right
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class ErrorConversionTest {
    private object IoError

    private fun toError(throwable: Throwable): IoError? = if (throwable is IOException) IoError else null

    @Test
    fun suspend_success_isRight() = runTest {
        assertEquals(1.right(), catchNamed(::toError) { 1 })
    }

    @Test
    fun suspend_namedException_isLeft() = runTest {
        assertEquals(IoError.left(), catchNamed(::toError) { throw IOException() })
    }

    @Test(expected = IllegalStateException::class)
    fun suspend_unnamedException_isRethrown() = runTest {
        catchNamed(::toError) { throw IllegalStateException() }
    }

    @Test(expected = CancellationException::class)
    fun suspend_cancellation_isRethrown() = runTest {
        catchNamed({ IoError }) { throw CancellationException() }
    }

    @Test
    fun flow_values_areRight() = runTest {
        assertEquals(listOf(1.right(), 2.right()), flowOf(1, 2).catchNamed(::toError).toList())
    }

    @Test
    fun flow_namedException_emitsLeftAndEnds() = runTest {
        val upstream = flow {
            emit(1)
            throw IOException()
        }

        assertEquals(listOf(1.right(), IoError.left()), upstream.catchNamed(::toError).toList())
    }

    @Test(expected = IllegalStateException::class)
    fun flow_unnamedException_isRethrown() = runTest {
        flow<Int> { throw IllegalStateException() }.catchNamed(::toError).toList()
    }
}
