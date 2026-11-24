package com.eduardoflores.rolabox.common.designsystem.screenshot

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import app.cash.paparazzi.Snapshot
import app.cash.paparazzi.SnapshotHandler
import com.android.ide.common.rendering.api.SessionParams.RenderingMode
import com.android.resources.Density
import com.android.resources.NightMode
import com.android.resources.ScreenOrientation
import com.android.resources.ScreenRatio
import java.awt.AlphaComposite
import java.awt.Color
import java.awt.GradientPaint
import java.awt.RenderingHints
import java.awt.geom.Ellipse2D
import java.awt.image.BufferedImage
import java.io.File
import java.util.Random
import javax.imageio.ImageIO
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule

// The store run sets these (ScreenshotTests.kt in build-logic, ADR-039 rule 2).
private const val RUN_PROPERTY = "rolabox.storeScreenshots"
private const val OUT_PROPERTY = "rolabox.storeScreenshots.out"
private const val ART_PROPERTY = "rolabox.storeScreenshots.art"

// A Pixel 6 is 1080 × 2400, which Play takes as it is (ADR-039).
private val STORE_DEVICE = DeviceConfig.PIXEL_6

/** Which images a case is for. Play's are made up; the README's may name real things (ADR-039 rules 4 to 6). */
enum class StoreSet(internal val folder: String) {
    Play("play"),
    Readme("readme"),
}

/**
 * A cover as [width] by [height] ARGB pixels, row by row: the shape of the library's `ArtworkImage`, which the design
 * system can't see, so a test turns it into one.
 */
class CoverPixels(val width: Int, val height: Int, val pixels: IntArray)

/**
 * Base of a module's `StoreScreenshotTest` (ADR-039). Each test calls [shoot] once per screen. It renders the
 * screen at the Pixel 6's full resolution, in light and then in dark, and writes
 * `build/store-screenshots/<set>/<name>_Light.png` and `_Dark.png`. Nothing is compared against a golden.
 *
 * Play's icon and feature graphic go through [shootGraphic], at their own sizes (ADR-041).
 *
 * Its tests run only under `./gradlew storeScreenshots`. Every other run filters them out, and a run that still
 * reaches one skips it.
 */
open class StoreScreenshot {
    private val pngWriter = PngWriter()

    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = STORE_DEVICE,
        renderingMode = RenderingMode.NORMAL,
        snapshotHandler = pngWriter,
        useDeviceResolution = true,
    )

    @Before
    fun onlyInTheStoreRun() {
        assumeTrue(
            "Store screenshots run only with ./gradlew storeScreenshots",
            System.getProperty(RUN_PROPERTY) == "true",
        )
    }

    /** Renders [content], a whole screen in its `RolaboxTheme`, as `<set>/<name>`, in light and in dark. */
    protected fun shoot(set: StoreSet, name: String, content: @Composable () -> Unit) {
        pngWriter.keepAlpha = false
        for (night in listOf(false, true)) {
            paparazzi.unsafeUpdateConfig(
                STORE_DEVICE.copy(nightMode = if (night) NightMode.NIGHT else NightMode.NOTNIGHT),
            )
            paparazzi.snapshot("${set.folder}/${name}_${if (night) "Dark" else "Light"}") {
                Box(Modifier.fillMaxSize()) { content() }
            }
        }
    }

    /**
     * Renders [content] as one of Play's listing graphics (ADR-041): exactly [width] × [height] pixels, in one theme
     * ([night] or not), to `build/store-screenshots/play/<name>.png`. Screenshots use [shoot] instead.
     *
     * [dpi] is the screen density it renders at, the Pixel 6's unless given. A higher one draws everything larger
     * and sharper in the same pixels, as a denser screen would, with the font scale left at 1.0 (ADR-017 rule 2).
     *
     * [alpha] keeps the alpha channel. Only the icon wants it (a 32-bit PNG); Play takes the feature graphic and
     * the screenshots only as 24-bit PNGs, with no alpha.
     */
    protected fun shootGraphic(
        name: String,
        width: Int,
        height: Int,
        night: Boolean = false,
        dpi: Int? = null,
        alpha: Boolean = false,
        content: @Composable () -> Unit,
    ) {
        pngWriter.keepAlpha = alpha
        paparazzi.unsafeUpdateConfig(
            STORE_DEVICE.copy(
                screenWidth = width,
                screenHeight = height,
                xdpi = dpi ?: STORE_DEVICE.xdpi,
                ydpi = dpi ?: STORE_DEVICE.ydpi,
                density = dpi?.let(Density::create) ?: STORE_DEVICE.density,
                orientation = if (width > height) ScreenOrientation.LANDSCAPE else ScreenOrientation.PORTRAIT,
                ratio = ScreenRatio.NOTLONG,
                nightMode = if (night) NightMode.NIGHT else NightMode.NOTNIGHT,
            ),
        )
        paparazzi.snapshot("${StoreSet.Play.folder}/$name") {
            Box(Modifier.fillMaxSize()) { content() }
        }
    }
}

/**
 * Writes each frame as a PNG named after its snapshot, under the store run's output folder: 24-bit RGB, unless
 * [keepAlpha] asks for 32-bit ARGB.
 */
private class PngWriter : SnapshotHandler {
    var keepAlpha = false

    override fun newFrameHandler(snapshot: Snapshot, frameCount: Int, fps: Int) =
        object : SnapshotHandler.FrameHandler {
            override fun handle(image: BufferedImage) {
                val out =
                    File(checkNotNull(System.getProperty(OUT_PROPERTY)) { "No $OUT_PROPERTY" }, "${snapshot.name}.png")
                out.parentFile.mkdirs()
                ImageIO.write(if (keepAlpha) image else image.withoutAlpha(), "PNG", out)
            }

            override fun close() = Unit
        }

    override fun close() = Unit
}

/** The same pixels as 24-bit RGB. The screens are opaque, so nothing shows through where the alpha was. */
private fun BufferedImage.withoutAlpha(): BufferedImage {
    val rgb = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
    val graphics = rgb.createGraphics()
    try {
        graphics.drawImage(this, 0, 0, null)
    } finally {
        graphics.dispose()
    }
    return rgb
}

/**
 * A made-up cover for the Play set (ADR-039 rule 4): a diagonal blend from [from] to [to], with a few soft circles of
 * [accent] placed by [seed], so the same arguments always draw the same cover. Colors are ARGB, as `0xFF336699`.
 */
fun colorMixCover(from: Long, to: Long, accent: Long, seed: Long, size: Int = COVER_SIZE): CoverPixels {
    val image = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)
    val graphics = image.createGraphics()
    try {
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        graphics.paint =
            GradientPaint(0f, 0f, Color(from.toInt(), true), size.toFloat(), size.toFloat(), Color(to.toInt(), true))
        graphics.fillRect(0, 0, size, size)
        val random = Random(seed)
        repeat(CIRCLES) {
            val diameter = size * (MIN_CIRCLE + random.nextFloat() * (MAX_CIRCLE - MIN_CIRCLE))
            val x = random.nextFloat() * size - diameter / 2
            val y = random.nextFloat() * size - diameter / 2
            graphics.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, CIRCLE_ALPHA)
            graphics.color = Color(accent.toInt(), true)
            graphics.fill(Ellipse2D.Float(x, y, diameter, diameter))
        }
    } finally {
        graphics.dispose()
    }
    return image.toCoverPixels()
}

/**
 * Real art for the README set, read from `.screenshots-local/[fileName]` and scaled to [size] square (ADR-039 rule
 * 5). The folder is never committed, so on a machine without the file the case is skipped, not failed.
 */
fun localArtwork(fileName: String, size: Int = COVER_SIZE): CoverPixels {
    val file = File(checkNotNull(System.getProperty(ART_PROPERTY)) { "No $ART_PROPERTY" }, fileName)
    assumeTrue("${file.path} is missing: docs/releasing.md says where it comes from", file.isFile)
    val source = checkNotNull(ImageIO.read(file)) { "${file.path} isn't an image ImageIO can read" }
    val image = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)
    val graphics = image.createGraphics()
    try {
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
        graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
        graphics.drawImage(source, 0, 0, size, size, null)
    } finally {
        graphics.dispose()
    }
    return image.toCoverPixels()
}

private fun BufferedImage.toCoverPixels() = CoverPixels(width, height, getRGB(0, 0, width, height, null, 0, width))

// Big enough to stay sharp where Now Playing draws the cover at 1080 px wide.
private const val COVER_SIZE = 640
private const val CIRCLES = 5
private const val MIN_CIRCLE = 0.25f
private const val MAX_CIRCLE = 0.7f
private const val CIRCLE_ALPHA = 0.35f
