plugins {
    id("rolabox.jvm.library")
    id("rolabox.navigation")
    // A section's row carries a composable, which reads its title from the contributor's resources.
    id("rolabox.jvm.compose")
}

dependencies {
    testImplementation(libs.junit)
}
