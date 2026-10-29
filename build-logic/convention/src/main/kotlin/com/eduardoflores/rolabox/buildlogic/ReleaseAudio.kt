package com.eduardoflores.rolabox.buildlogic

import com.android.build.api.artifact.SingleArtifact
import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipFile
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.kotlin.dsl.configure

/**
 * The only audio a release may contain: the design system's UI sounds. A release build shortens
 * resource paths (`res/raw/ds_wheel_tick.wav` ships as something like `res/qL.wav`), so an entry is
 * allowed by its bytes, which must be those of one of these files.
 */
internal val ALLOWED_RELEASE_AUDIO = listOf(
    "common/designsystem/src/main/res/raw/ds_wheel_press.wav",
    "common/designsystem/src/main/res/raw/ds_wheel_tick.wav",
)

private val AUDIO_EXTENSIONS = setOf(
    "mp3", "m4a", "m4b", "aac", "wav", "ogg", "oga", "opus", "flac", "amr", "awb", "mid", "midi", "wma", "aif", "aiff",
)
private const val HEADER_BYTES = 12

/**
 * Fails a release build that contains audio other than [ALLOWED_RELEASE_AUDIO], such as a test
 * fixture or a song left in assets. It inspects what was packaged, the APK and the bundle, so audio
 * that arrives through any module or dependency is caught. It runs after `assembleRelease` and
 * `bundleRelease`, and `check` runs it on the APK.
 */
internal fun Project.registerReleaseAudioCheck() {
    val allowed = ALLOWED_RELEASE_AUDIO.map { rootProject.file(it) }
    extensions.configure<ApplicationAndroidComponentsExtension> {
        onVariants(selector().withBuildType("release")) { variant ->
            val name = variant.name.replaceFirstChar(Char::uppercase)
            val apkCheck = tasks.register("check${name}ApkAudio", CheckReleaseAudioTask::class.java) {
                group = "verification"
                description = "Checks that the ${variant.name} APK contains no audio but the UI sounds."
                archives.from(variant.artifacts.get(SingleArtifact.APK))
                allowedAudio.from(allowed)
                marker.set(layout.buildDirectory.file("intermediates/check${name}ApkAudio/ok"))
            }
            val bundleCheck = tasks.register("check${name}BundleAudio", CheckReleaseAudioTask::class.java) {
                group = "verification"
                description = "Checks that the ${variant.name} bundle contains no audio but the UI sounds."
                archives.from(variant.artifacts.get(SingleArtifact.BUNDLE))
                allowedAudio.from(allowed)
                marker.set(layout.buildDirectory.file("intermediates/check${name}BundleAudio/ok"))
            }
            tasks.matching { it.name == "assemble$name" }.configureEach { finalizedBy(apkCheck) }
            tasks.matching { it.name == "bundle$name" }.configureEach { finalizedBy(bundleCheck) }
            tasks.matching { it.name == "check" }.configureEach { dependsOn(apkCheck) }
        }
    }
}

@CacheableTask
internal abstract class CheckReleaseAudioTask : DefaultTask() {
    /** APKs and bundles, or folders that hold them. */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val archives: ConfigurableFileCollection

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val allowedAudio: ConfigurableFileCollection

    @get:OutputFile
    abstract val marker: RegularFileProperty

    @TaskAction
    fun check() {
        val allowed = allowedAudio.files.associateBy({ it.readBytes().sha256() }, { it.name })
        val packages = archives.files.flatMap { file ->
            if (file.isDirectory) file.walk().filter { it.extension == "apk" || it.extension == "aab" }.toList() else listOf(file)
        }
        val violations = packages.flatMap { archive -> releaseAudioViolations(archive, allowed.keys).map { "${archive.name}: $it" } }
        if (violations.isNotEmpty()) {
            throw GradleException(
                "A release must contain no audio but the UI sounds. Found:\n" +
                    violations.joinToString("\n") { "  - $it" } +
                    "\nAllowed: ${ALLOWED_RELEASE_AUDIO.joinToString()}. Test audio comes from " +
                    ":device:library:testing, from test configurations only (ADR-020 rule 7).",
            )
        }
        marker.get().asFile.writeText("ok")
    }
}

/** The audio entries of an APK or bundle whose bytes aren't one of [allowedSha256], each with what it is. */
internal fun releaseAudioViolations(archive: File, allowedSha256: Set<String>): List<String> =
    ZipFile(archive).use { zip ->
        zip.entries().asSequence().filterNot { it.isDirectory }.mapNotNull { entry ->
            val bytes = zip.getInputStream(entry).use { it.readBytes() }
            val kind = audioKind(entry.name, bytes) ?: return@mapNotNull null
            if (bytes.sha256() in allowedSha256) null else "${entry.name} ($kind)"
        }.sorted().toList()
    }

/** What audio a file is, by its extension or its first bytes, or null when it isn't audio. */
internal fun audioKind(name: String, bytes: ByteArray): String? {
    val header = bytes.copyOf(HEADER_BYTES)
    fun ascii(at: Int, length: Int) = String(header, at, length, Charsets.ISO_8859_1)
    fun byte(at: Int) = header[at].toInt() and 0xFF
    val extension = name.substringAfterLast('.', "").lowercase()
    return when {
        ascii(0, 3) == "ID3" -> "MP3 with an ID3 tag"
        isMpegAudioFrame(byte(0), byte(1), byte(2)) -> "MPEG audio"
        byte(0) == 0xFF && byte(1) and 0xF6 == 0xF0 -> "AAC (ADTS)"
        ascii(4, 4) == "ftyp" && ascii(8, 3) in setOf("M4A", "M4B", "M4P") -> "M4A"
        ascii(0, 4) == "RIFF" && ascii(8, 4) == "WAVE" -> "WAV"
        ascii(0, 4) == "OggS" -> "Ogg"
        ascii(0, 4) == "fLaC" -> "FLAC"
        ascii(0, 5) == "#!AMR" -> "AMR"
        ascii(0, 4) == "MThd" -> "MIDI"
        extension in AUDIO_EXTENSIONS -> "a .$extension file"
        else -> null
    }
}

// An MPEG audio frame header: the sync, a known version and layer, and a valid bitrate and sample rate.
private fun isMpegAudioFrame(first: Int, second: Int, third: Int): Boolean {
    val synced = first == 0xFF && second and 0xE0 == 0xE0
    val version = second shr 3 and 0x3
    val layer = second shr 1 and 0x3
    val bitrate = third shr 4
    val sampleRate = third shr 2 and 0x3
    return synced && version != 0b01 && layer != 0b00 && bitrate != 0 && bitrate != 0xF && sampleRate != 0x3
}

private fun ByteArray.sha256(): String =
    MessageDigest.getInstance("SHA-256").digest(this).joinToString("") { "%02x".format(it) }
