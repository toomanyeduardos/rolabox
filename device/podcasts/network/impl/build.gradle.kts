plugins {
    id("rolabox.android.library")
    id("rolabox.android.robolectric")
    id("rolabox.hilt")
}

android {
    namespace = "com.eduardoflores.rolabox.device.podcasts.network.impl"
}

dependencies {
    implementation(project(":device:podcasts:api"))
    implementation(project(":device:podcasts:network:api"))
    implementation(project(":common:util"))
    implementation(libs.arrow.core)
    implementation(libs.kotlinx.coroutines.core)
    // HTML descriptions become plain text with HtmlCompat (ADR-031, Feeds).
    implementation(libs.androidx.core.ktx)
    // HTTP and JSON are dependencies of :network:impl modules only (ADR-027, rule 7).
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    // The feeds are parsed with the platform's XmlPullParser and HtmlCompat, on the JVM (ADR-031, Feeds).
    testImplementation(libs.robolectric)
    // Recorded answers are served from a local server, which follows redirects and stalls as a real one does.
    testImplementation(libs.okhttp.mockwebserver)
}
