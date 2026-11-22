import com.mikepenz.aboutlibraries.plugin.AboutLibrariesExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

/**
 * Generates the list of open-source libraries that About's Open-source licences shows (launch/06), with the
 * AboutLibraries plugin. Applied to `:app`, which depends on every `:impl`: each variant's list is built from that
 * variant's runtime classpath, so the release build lists every library the release app ships. The list is written
 * as `res/raw/aboutlibraries.json` into the variant's generated resources, and read by `:device:settings:impl`.
 *
 * Only the plugin is used, not its runtime library, which brings kotlinx-serialization-json (ADR-027, rule 7).
 * It runs offline, so a build never depends on the network. The texts of the open-source licences are committed in
 * `config/aboutlibraries/licenses/`, copied from SPDX, and matched to a library by the licence's SPDX id. A licence
 * without a text there, such as Google's SDK terms, is shown by its URL. A new dependency under a licence that has no
 * text there makes the plugin warn, and its text can be added the same way.
 */
class RolaboxAndroidApplicationLicencesConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.mikepenz.aboutlibraries.plugin.android")
            extensions.configure<AboutLibrariesExtension> {
                offlineMode.set(true)
                collect {
                    configPath.set(rootProject.layout.projectDirectory.dir("config/aboutlibraries"))
                    fetchRemoteLicense.set(false)
                    fetchRemoteFunding.set(false)
                    includePlatform.set(false)
                }
                export {
                    // What About doesn't read stays out of the APK.
                    excludeFields.addAll("developers", "funding", "description", "organization", "scm")
                    prettyPrint.set(false)
                }
            }
        }
    }
}
