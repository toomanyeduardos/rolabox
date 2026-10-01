plugins {
    `java-library`
    id("org.jetbrains.kotlin.jvm") version libs.versions.kotlin
}

// The coordinates the main build asks for in `detektPlugins`, which Gradle swaps for this project.
group = "com.eduardoflores.rolabox.buildlogic"

// The rules are loaded by detekt's own JVM, so they target a fixed bytecode level, not the JDK that
// happens to build them.
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions.jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
}

dependencies {
    compileOnly(libs.detekt.api)

    testImplementation(libs.detekt.api)
    testImplementation(libs.detekt.test)
    testImplementation(libs.junit)
}
