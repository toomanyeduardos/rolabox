package com.eduardoflores.rolabox.core.common

import javax.inject.Qualifier

/**
 * A `CoroutineScope` that lives as long as the app, for work that must finish even if the screen
 * that started it goes away (ADR-006). Never use `GlobalScope` for this.
 */
@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class ApplicationScope
