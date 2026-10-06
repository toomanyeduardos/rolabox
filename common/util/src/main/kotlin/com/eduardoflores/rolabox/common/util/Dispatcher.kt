package com.eduardoflores.rolabox.common.util

import javax.inject.Qualifier

@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class Dispatcher(val dispatcher: RolaboxDispatchers)

enum class RolaboxDispatchers {
    Default,
    IO,
}
