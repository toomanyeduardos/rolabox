plugins {
    id("rolabox.jvm.library")
}

dependencies {
    implementation(project(":device:library:api"))
    implementation(project(":device:library:local:api"))
    implementation(project(":common:storage:api"))
    implementation(libs.javax.inject)

    testImplementation(libs.junit)
}

// Puts an audio fixture set on a connected device for manual testing: -Pset=clean|broken|formats|large|all|<folder>.
val projectRoot: String = rootDir.path
tasks.register<JavaExec>("pushAudioFixtures") {
    group = "rolabox"
    description =
        "Copies an audio fixture set (-Pset=clean|broken|formats|large|all, or a folder) to the device's /Music."
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.eduardoflores.rolabox.device.library.testing.audio.PushAudioFixturesKt")
    args(providers.gradleProperty("set").getOrElse("clean"), projectRoot)
}
