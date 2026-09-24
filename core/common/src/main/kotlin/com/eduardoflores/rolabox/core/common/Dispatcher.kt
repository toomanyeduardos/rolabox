package com.eduardoflores.rolabox.core.common

import javax.inject.Qualifier

@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class Dispatcher(val dispatcher: RolaboxDispatchers)

enum class RolaboxDispatchers {
    Default,
    IO,
}
