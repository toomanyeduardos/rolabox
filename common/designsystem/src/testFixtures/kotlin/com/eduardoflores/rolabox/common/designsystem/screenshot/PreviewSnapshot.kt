package com.eduardoflores.rolabox.common.designsystem.screenshot

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

/**
 * One screenshot: a `@Preview` composable at a font scale. Its [toString] names the golden, after the
 * composable, the light or dark preview it is ([nameSource]), and the font scale when it isn't 1.0.
 */
class PreviewCase internal constructor(
    internal val preview: ComposablePreview<AndroidPreviewInfo>,
    internal val fontScale: Float,
    nameSource: AndroidPreviewInfo = preview.previewInfo,
) {
    private val name = buildString {
        append(preview.declaringClass.substringAfterLast('.').removeSuffix("Kt"))
        append('_').append(preview.methodName)
        nameSource.name.takeIf { it.isNotBlank() }?.let { append('_').append(it) }
        if (fontScale != 1f) append("_font").append((fontScale * 10).toInt())
    }.replace(Regex("[^A-Za-z0-9_]"), "_")

    override fun toString() = name
}

/**
 * Every `@Preview` under [packages], including `private` ones, at the font scale it declares. For
 * `@PreviewLightDark` that is light, dark and light at 1.5x. A composable whose previews declare no
 * font scale (a bare `@Preview`) gets its light previews again at 1.5x, so every composable has the
 * three renders of ADR-016. Fails if there are none.
 */
fun previewCases(vararg packages: String): List<PreviewCase> = AndroidComposablePreviewScanner()
    .scanPackageTrees(*packages)
    .includePrivatePreviews()
    .getPreviews()
    .groupBy { it.declaringClass to it.methodName }
    .values
    .flatMap { previews ->
        val (scaled, plain) = previews.partition { it.previewInfo.fontScale != 1f }
        val light = plain.filterNot { it.previewInfo.isNight() }
        plain.map { PreviewCase(it, fontScale = 1f) } +
            if (scaled.isEmpty()) {
                light.map { PreviewCase(it, LARGE_FONT_SCALE) }
            } else {
                // A large-font golden is named after the light preview it is the large version of, not
                // after its own label in the IDE, so both ways of asking for it give the same file.
                scaled.map { preview ->
                    val info = preview.previewInfo
                    val plainTwin = (if (info.isNight()) plain - light.toSet() else light).firstOrNull()
                    PreviewCase(preview, info.fontScale, nameSource = plainTwin?.previewInfo ?: info)
                }
            }
    }
    .sortedBy { it.toString() }
    .also { cases ->
        // Copying a module's test and forgetting the package would otherwise pass with nothing to check.
        check(cases.isNotEmpty()) { "No @Preview composables under ${packages.joinToString()}. Is the package right?" }
        // Two cases with one name would record over each other, and one of them would go unchecked.
        val clashes = cases.groupBy { it.toString() }.filterValues { it.size > 1 }.keys
        check(clashes.isEmpty()) { "Previews that would share a golden: ${clashes.joinToString()}. Name them apart." }
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
 *         fun cases() = previewCases("com.eduardoflores.rolabox.auth.ui.impl")
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
