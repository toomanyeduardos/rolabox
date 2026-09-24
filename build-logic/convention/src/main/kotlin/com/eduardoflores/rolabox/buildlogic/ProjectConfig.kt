package com.eduardoflores.rolabox.buildlogic

import org.gradle.api.JavaVersion

object ProjectConfig {
    const val COMPILE_SDK = 37
    const val MIN_SDK = 29
    const val TARGET_SDK = 37
    val JAVA_VERSION: JavaVersion = JavaVersion.VERSION_11
}
