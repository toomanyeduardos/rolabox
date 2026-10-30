package com.eduardoflores.rolabox.device.library.testing.audio

import java.io.File
import java.nio.file.Files
import java.util.Properties
import kotlin.system.exitProcess

private const val DEVICE_FOLDER = "/sdcard/Music/RolaboxFixtures"
private const val ALL_SETS = "all"
private const val SCAN_FILE_ACTION = "android.intent.action.MEDIA_SCANNER_SCAN_FILE"
private const val APP_PACKAGE = "com.eduardoflores.rolabox"

// The audio permission from API 33, and the storage permission before it.
private val AUDIO_PERMISSIONS =
    listOf("android.permission.READ_MEDIA_AUDIO", "android.permission.READ_EXTERNAL_STORAGE")

/**
 * Puts a fixture set on the connected phone or emulator, as real music would be: copied into
 * [DEVICE_FOLDER], then scanned by MediaStore. Run through Gradle:
 *
 * ```
 * ./gradlew :device:library:testing:pushAudioFixtures -Pset=broken
 * ```
 *
 * `set` is `clean`, `broken`, `formats` or `all`, or the path of a folder of your own music. Each
 * set replaces what an earlier push of it left. With several devices, set `ANDROID_SERIAL`.
 *
 * When the app is installed, it is also granted the audio permission, which the debug build's stand-in
 * library needs to read MediaStore and which nothing asks for until library/04.
 *
 * Arguments: the set, and the root of the project, whose `local.properties` may name the SDK.
 */
fun main(args: Array<String>) {
    val (setName, projectRoot) = args
    val adb = Adb(adbPath(File(projectRoot)))

    val folder = File(setName)
    val sources: List<Pair<String, File>> = when {
        folder.isDirectory -> listOf(folder.name to folder)

        setName == ALL_SETS -> AudioFixtures.sets.map { it.name to it.writeToTemporaryFolder() }

        else -> {
            val set = AudioFixtures.named(setName) ?: fail(
                "No fixture set or folder '$setName'. Use ${AudioFixtures.sets.joinToString { it.name }}, " +
                    "$ALL_SETS, or the path of a folder.",
            )
            listOf(set.name to set.writeToTemporaryFolder())
        }
    }

    sources.forEach { (name, source) ->
        val target = "$DEVICE_FOLDER/$name"
        adb.run("shell", "rm", "-rf", target)
        adb.run("shell", "mkdir", "-p", target)
        // The folder's contents, not the folder, so a folder of any name lands at target.
        source.listFiles().orEmpty().sortedBy { it.name }.forEach { adb.run("push", it.path, target) }
        // MediaStore scans a folder recursively, and forgets the files that the rm above removed.
        adb.run("shell", "am", "broadcast", "-a", SCAN_FILE_ACTION, "-d", "file://$target")
        println("Pushed $name to $target")
    }
    grantAudioPermission(adb)
}

private fun grantAudioPermission(adb: Adb) {
    // Only one of the two exists on a given API level, and neither while the app isn't installed.
    if (AUDIO_PERMISSIONS.any { adb.succeeds("shell", "pm", "grant", APP_PACKAGE, it) }) {
        println("Granted $APP_PACKAGE the audio permission")
    } else {
        println("Install the debug app and push again to grant it the audio permission")
    }
}

private fun AudioFixtureSet.writeToTemporaryFolder(): File =
    Files.createTempDirectory("rolabox-$name").toFile().also { writeTo(it) }

// ANDROID_HOME, then local.properties' sdk.dir, then adb on the PATH.
private fun adbPath(projectRoot: File): String {
    val sdk = System.getenv("ANDROID_HOME")
        ?: System.getenv("ANDROID_SDK_ROOT")
        ?: File(projectRoot, "local.properties").takeIf { it.isFile }?.let { file ->
            Properties().apply { file.inputStream().use(::load) }.getProperty("sdk.dir")
        }
    val adb = sdk?.let { File(it, "platform-tools/adb") }
    return if (adb != null && adb.canExecute()) adb.path else "adb"
}

private class Adb(private val path: String) {
    fun run(vararg args: String) {
        val process = ProcessBuilder(listOf(path) + args).inheritIO().start()
        val exit = process.waitFor()
        if (exit != 0) fail("adb ${args.joinToString(" ")} failed ($exit). Is a device connected? (adb devices)")
    }

    /** Runs adb without its output, and says whether it succeeded. */
    fun succeeds(vararg args: String): Boolean = ProcessBuilder(listOf(path) + args)
        .redirectOutput(ProcessBuilder.Redirect.DISCARD)
        .redirectError(ProcessBuilder.Redirect.DISCARD)
        .start()
        .waitFor() == 0
}

private fun fail(message: String): Nothing {
    System.err.println(message)
    exitProcess(1)
}
