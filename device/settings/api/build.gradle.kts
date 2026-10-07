plugins {
    id("rolabox.jvm.library")
    id("rolabox.navigation")
    // A section's row carries a composable, which reads its title from the contributor's resources.
    id("rolabox.jvm.compose")
}

dependencies {
    // Flow appears in a section's public signature.
    api(libs.kotlinx.coroutines.core)
    testImplementation(libs.junit)
}
