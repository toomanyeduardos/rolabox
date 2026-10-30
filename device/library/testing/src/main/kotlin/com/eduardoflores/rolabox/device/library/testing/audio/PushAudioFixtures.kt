package com.eduardoflores.rolabox.device.library.testing.audio

import java.io.File
import java.nio.file.Files
import java.util.Properties
import kotlin.system.exitProcess

private const val DEVICE_FOLDER = "/sdcard/Music/RolaboxFixtures"
private const val ALL_SETS = "all"
private const val SCAN_FILE_ACTION = "android.intent.action.MEDIA_SCANNER_SCAN_FILE"

// The debug build's stand-in library reads the app's own files, with no permission. library/03 removes these.
private const val APP_PACKAGE = "com.eduardoflores.rolabox"
private const val APP_FOLDER = "files/RolaboxFixtures"
private const val TEMPORARY_FOLDER = "/data/local/tmp/RolaboxFixtures"

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
 * Each set is also copied into the installed debug app's own files, which is what its stand-in library lists until
 * library/03. Uninstalling the app deletes that copy.
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
        adb.pushContents(source, target)
        // MediaStore scans a folder recursively, and forgets the files that the rm in pushContents removed.
        adb.run("shell", "am", "broadcast", "-a", SCAN_FILE_ACTION, "-d", "file://$target")
        println("Pushed $name to $target")
        adb.copyIntoApp(source, name)
    }
}

// adb can't write an app's files, but a debug build's own user can, through run-as. So the set goes through a
// folder both can read.
private fun Adb.copyIntoApp(source: File, name: String) {
    val temporary = "$TEMPORARY_FOLDER/$name"
    val target = "$APP_FOLDER/$name"
    pushContents(source, temporary)
    val copied = succeeds(
        "shell",
        "run-as $APP_PACKAGE sh -c 'rm -rf \"$target\" && mkdir -p \"$target\" && cp -r \"$temporary/.\" \"$target/\"'",
    )
    run("shell", "rm", "-rf", TEMPORARY_FOLDER)
    println(if (copied) "Copied $name into the app" else "Install the debug app and push again to play $name")
}

// The folder's contents, not the folder, so a folder of any name lands at target.
private fun Adb.pushContents(source: File, target: String) {
    run("shell", "rm", "-rf", target)
    run("shell", "mkdir", "-p", target)
    source.listFiles().orEmpty().sortedBy { it.name }.forEach { run("push", it.path, target) }
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
