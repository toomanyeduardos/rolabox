package com.eduardoflores.rolabox.device.ui.impl

import com.eduardoflores.rolabox.common.designsystem.screenshot.PreviewCase
import com.eduardoflores.rolabox.common.designsystem.screenshot.PreviewSnapshot
import com.eduardoflores.rolabox.common.designsystem.screenshot.previewCases
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/** Screenshots of every `@Preview` in this module (ADR-016). */
@RunWith(Parameterized::class)
class PreviewSnapshotTest(case: PreviewCase) : PreviewSnapshot(case) {
    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun cases() = previewCases("com.eduardoflores.rolabox.device.ui.impl")
    }
}
