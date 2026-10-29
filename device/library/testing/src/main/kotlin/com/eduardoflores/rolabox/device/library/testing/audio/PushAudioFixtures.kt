package com.eduardoflores.rolabox.device.library.testing.audio

import java.io.File
import java.nio.file.Files
import java.util.Properties
import kotlin.system.exitProcess

private const val DEVICE_FOLDER = "/sdcard/Music/RolaboxFixtures"
private const val ALL_SETS = "all"
private const val SCAN_FILE_ACTION = "android.intent.action.MEDIA_SCANNER_SCAN_FILE"

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
}

private fun fail(message: String): Nothing {
    System.err.println(message)
    exitProcess(1)
}
