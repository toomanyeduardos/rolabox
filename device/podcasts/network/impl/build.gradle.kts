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
    implementation(project(":common:sync:api"))
    // The follows (ADR-031, Syncing follows) and the choices of episode order (ADR-043) sync through Firestore: its
    // one provider, and the conversion of its exceptions (ADR-033). Firestore is a dependency of :network:impl modules
    // and :common:firestore only.
    implementation(project(":common:firestore"))
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.firestore)
    // Awaits Firebase's Tasks.
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.arrow.core)
    implementation(libs.kotlinx.coroutines.core)
    // HTML descriptions become plain text with HtmlCompat (ADR-031, Feeds).
    implementation(libs.androidx.core.ktx)
    // HTTP and JSON are dependencies of :network:impl modules only (ADR-027, rule 7).
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(project(":common:testing"))
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    // The feeds are parsed with the platform's XmlPullParser and HtmlCompat, on the JVM (ADR-031, Feeds).
    testImplementation(libs.robolectric)
    // Recorded answers are served from a local server, which follows redirects and stalls as a real one does.
    testImplementation(libs.okhttp.mockwebserver)
}
