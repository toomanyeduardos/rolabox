plugins {
    id("rolabox.android.library")
    id("rolabox.android.compose")
    id("rolabox.android.paparazzi")
}

android {
    namespace = "com.eduardoflores.rolabox.core.designsystem"

    // The screenshot test harness other modules' tests use (ADR-016).
    testFixtures {
        enable = true
    }
}

dependencies {
    api(libs.androidx.compose.material3)

    testImplementation(libs.junit)

    testFixturesApi(libs.paparazzi)
    testFixturesApi(libs.junit)
    testFixturesImplementation(libs.composable.preview.scanner)
    testFixturesImplementation(platform(libs.androidx.compose.bom))
    testFixturesImplementation(libs.androidx.compose.ui)
}
