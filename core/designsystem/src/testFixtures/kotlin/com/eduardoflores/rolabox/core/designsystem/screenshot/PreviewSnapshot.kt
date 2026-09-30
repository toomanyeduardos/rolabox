package com.eduardoflores.rolabox.core.designsystem.screenshot

import android.content.res.Configuration.UI_MODE_NIGHT_MASK
import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.ide.common.rendering.api.SessionParams.RenderingMode
import com.android.resources.NightMode
import org.junit.Rule
import org.junit.Test
import sergio.sastre.composable.preview.scanner.android.AndroidComposablePreviewScanner
import sergio.sastre.composable.preview.scanner.android.AndroidPreviewInfo
import sergio.sastre.composable.preview.scanner.core.preview.ComposablePreview

private const val LARGE_FONT_SCALE = 1.5f
private val DEVICE = DeviceConfig.PIXEL_6

/** One screenshot: a `@Preview` composable at a font scale. Its [toString] names the golden. */
class PreviewCase internal constructor(
    internal val preview: ComposablePreview<AndroidPreviewInfo>,
    internal val fontScale: Float,
) {
    private val name = buildString {
        append(preview.declaringClass.substringAfterLast('.').removeSuffix("Kt"))
        append('_').append(preview.methodName)
        preview.previewInfo.name.takeIf { it.isNotBlank() }?.let { append('_').append(it) }
        if (fontScale != 1f) append("_font").append((fontScale * 10).toInt())
    }.replace(Regex("[^A-Za-z0-9_]"), "_")

    override fun toString() = name
}

/**
 * Every `@Preview` under [packages], including `private` ones: each one as it is declared (for
 * `@PreviewLightDark`, in light and in dark), and the light ones again at 1.5x font scale. Fails if
 * there are none.
 */
fun previewCases(vararg packages: String): List<PreviewCase> = AndroidComposablePreviewScanner()
    .scanPackageTrees(*packages)
    .includePrivatePreviews()
    .getPreviews()
    .flatMap { preview ->
        buildList {
            add(PreviewCase(preview, fontScale = 1f))
            if (!preview.previewInfo.isNight()) add(PreviewCase(preview, LARGE_FONT_SCALE))
        }
    }
    .sortedBy { it.toString() }
    .also {
        // Copying a module's test and forgetting the package would otherwise pass with nothing to check.
        check(it.isNotEmpty()) { "No @Preview composables under ${packages.joinToString()}. Is the package right?" }
    }

private fun AndroidPreviewInfo.isNight() = uiMode and UI_MODE_NIGHT_MASK == UI_MODE_NIGHT_YES

/**
 * Base of a module's screenshot test (ADR-016). The module's test subclasses it, runs with
 * `Parameterized`, and returns [previewCases] for its packages:
 *
 * ```
 * @RunWith(Parameterized::class)
 * class PreviewSnapshotTest(case: PreviewCase) : PreviewSnapshot(case) {
 *     companion object {
 *         @JvmStatic
 *         @Parameterized.Parameters(name = "{0}")
 *         fun cases() = previewCases("com.eduardoflores.rolabox.feature.account")
 *     }
 * }
 * ```
 */
open class PreviewSnapshot(private val case: PreviewCase) {
    @get:Rule
    val paparazzi = Paparazzi(deviceConfig = DEVICE, renderingMode = RenderingMode.SHRINK)

    @Test
    fun snapshot() {
        val info = case.preview.previewInfo
        paparazzi.unsafeUpdateConfig(
            DEVICE.copy(
                nightMode = if (info.isNight()) NightMode.NIGHT else NightMode.NOTNIGHT,
                fontScale = case.fontScale,
            ),
        )
        paparazzi.snapshot {
            // What showBackground does in the IDE: the preview sits on a background, which is white
            // unless the preview names one.
            val background = when {
                !info.showBackground -> Color.Transparent
                info.backgroundColor != 0L -> Color(info.backgroundColor)
                else -> Color.White
            }
            Box(Modifier.background(background)) { case.preview() }
        }
    }
}
