package com.eduardoflores.rolabox.device.settings.impl

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Resources
import arrow.core.Either
import com.eduardoflores.rolabox.common.util.Dispatcher
import com.eduardoflores.rolabox.common.util.RolaboxDispatchers.IO
import com.eduardoflores.rolabox.common.util.catchNamed
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/** An open-source library the app ships, with its licences, as Open-Source Licences shows it. */
internal data class Licence(
    val id: String,
    val name: String,
    val version: String?,
    /** The names of the library's licences, joined, such as "The Apache License, Version 2.0". */
    val licenceName: String,
    /** The licences' texts, or their URLs for a licence whose text the app doesn't have, such as Google's SDK terms. */
    val text: String?,
)

/** The list of licences can't be read: the app was built without it, or it is damaged. */
internal data object LicencesUnavailable

/** The libraries the app ships, sorted by name. An interface so the ViewModels' tests don't need Android. */
internal fun interface Licences {
    suspend fun load(): Either<LicencesUnavailable, List<Licence>>
}

/**
 * Reads the list that the `rolabox.android.application.licences` plugin generates into `:app`'s resources, from the
 * variant's runtime classpath. Settings can't see `:app` (ADR-020), so the resource is found by its name.
 */
internal class GeneratedLicences @Inject constructor(
    @param:ApplicationContext private val context: Context,
    @param:Dispatcher(IO) private val dispatcher: CoroutineDispatcher,
) : Licences {
    override suspend fun load(): Either<LicencesUnavailable, List<Licence>> = withContext(dispatcher) {
        catchNamed(::toLicencesUnavailable) { parseLicences(readJson()) }
    }

    // The resource is :app's, generated at build time, so this module has no R field for it.
    @SuppressLint("DiscouragedApi")
    private fun readJson(): String {
        val id = context.resources.getIdentifier(RESOURCE_NAME, "raw", context.packageName)
        if (id == 0) throw Resources.NotFoundException("raw/$RESOURCE_NAME")
        return context.resources.openRawResource(id).bufferedReader().use { it.readText() }
    }

    private companion object {
        const val RESOURCE_NAME = "aboutlibraries"
    }
}

private fun toLicencesUnavailable(error: Throwable): LicencesUnavailable? = when (error) {
    is Resources.NotFoundException, is IOException, is JSONException -> LicencesUnavailable
    else -> null
}

/**
 * Parses AboutLibraries' JSON: `libraries`, each with a `uniqueId`, a `name`, an `artifactVersion` and the keys of its
 * `licenses`, and `licenses`, each key with a `name`, a `url` and the `content` when there is one.
 */
internal fun parseLicences(json: String): List<Licence> {
    val root = JSONObject(json)
    val licences = root.optJSONObject("licenses") ?: JSONObject()
    val libraries = root.getJSONArray("libraries")
    return (0 until libraries.length())
        .map { index -> libraries.getJSONObject(index).toLicence(licences) }
        .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
}

private fun JSONObject.toLicence(licences: JSONObject): Licence {
    val id = getString("uniqueId")
    val keys = optJSONArray("licenses")?.strings().orEmpty()
    val ofLibrary = keys.mapNotNull { licences.optJSONObject(it) }
    return Licence(
        id = id,
        name = optNonBlank("name") ?: id,
        version = optNonBlank("artifactVersion"),
        licenceName = ofLibrary.mapNotNull { it.optNonBlank("name") }.ifEmpty { keys }.joinToString(", "),
        text = ofLibrary.mapNotNull { it.optNonBlank("content") ?: it.optNonBlank("url") }
            .ifEmpty { null }
            ?.joinToString("\n\n"),
    )
}

private fun JSONArray.strings(): List<String> = (0 until length()).map(::getString)

private fun JSONObject.optNonBlank(name: String): String? = if (isNull(name)) null else optString(name).ifBlank { null }
