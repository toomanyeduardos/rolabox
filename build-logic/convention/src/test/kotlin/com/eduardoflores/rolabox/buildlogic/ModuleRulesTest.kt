package com.eduardoflores.rolabox.buildlogic

import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** A violating and a passing dependency for each rule that `ModuleRules.kt` checks. */
class ModuleRulesTest {
    @Test
    fun `rule 1 - common depends only on common`() {
        assertViolates("rule 1", ":common:sync:impl", ":auth:data:api")
        assertViolates("rule 1", ":common:designsystem", ":device:host")
        assertAllowed(":common:sync:impl", ":common:userdata:api")
        assertAllowed(":common:userdata:api", ":common:storage:api")
    }

    @Test
    fun `rule 2 - the device never depends on auth`() {
        assertViolates("rule 2", ":device:ui:impl", ":auth:ui:api")
        assertViolates("rule 2", ":device:settings:api", ":auth:data:api")
        assertViolates("rule 2", ":device:ui:impl", ":auth:data:testing", TEST)
        assertAllowed(":device:ui:impl", ":device:settings:api")
    }

    @Test
    fun `rule 3 - auth depends on the device only from the account section`() {
        assertViolates("rule 3", ":auth:ui:impl", ":device:settings:api")
        assertViolates("rule 3", ":auth:settings:impl", ":device:ui:api")
        assertViolates("rule 3", ":auth:settings:impl", ":device:host")
        assertAllowed(":auth:settings:impl", ":device:settings:api")
    }

    @Test
    fun `rule 4 - only app depends on an impl, at any depth`() {
        assertViolates("rule 4", ":auth:ui:impl", ":auth:data:impl")
        assertViolates("rule 4", ":device:ui:impl", ":device:settings:impl")
        assertViolates("rule 4", ":auth:data:testing", ":auth:data:impl")
        assertViolates("rule 4", ":auth:ui:impl", ":common:storage:impl", TEST)
        assertAllowed(":app", ":auth:data:impl")
        assertAllowed(":app", ":device:settings:impl")
    }

    @Test
    fun `rule 5 - an impl depends on apis, util and the design system`() {
        assertViolates("rule 5", ":auth:data:impl", ":some:module")
        assertAllowed(":auth:ui:impl", ":auth:data:api")
        assertAllowed(":auth:ui:impl", ":common:util")
        assertAllowed(":auth:ui:impl", ":common:designsystem")
        assertAllowed(":device:ui:impl", ":device:host")
        assertAllowed(":auth:ui:impl", ":auth:data:testing", TEST)
    }

    @Test
    fun `rule 6 - an api depends on apis and util`() {
        assertViolates("rule 6", ":auth:ui:api", ":common:designsystem")
        assertViolates("rule 6", ":auth:data:api", ":auth:data:testing", TEST)
        assertAllowed(":auth:data:api", ":common:storage:api")
        assertAllowed(":auth:data:api", ":common:util")
    }

    @Test
    fun `rule 7 - testing modules only from test configurations`() {
        assertViolates("rule 7", ":app", ":common:testing")
        assertViolates("rule 7", ":auth:ui:impl", ":auth:data:testing", "debugImplementation")
        assertAllowed(":app", ":common:testing", "androidTestImplementation")
        assertAllowed(":auth:ui:impl", ":auth:data:testing", "testDebugImplementation")
    }

    @Test
    fun `rule 8 - the host depends on the design system and util, and only device impls depend on it`() {
        assertViolates("rule 8", ":device:host", ":device:settings:api")
        assertViolates("rule 8", ":app", ":device:host")
        assertViolates("rule 8", ":device:ui:api", ":device:host")
        assertAllowed(":device:host", ":common:designsystem")
        assertAllowed(":device:host", ":common:util")
        assertAllowed(":device:ui:impl", ":device:host")
    }

    @Test
    fun `a module may use its own test fixtures`() {
        assertAllowed(":common:designsystem", ":common:designsystem", TEST)
    }

    @Test
    fun `ADR-005 rule 3 - an api has no Hilt`() {
        assertTrue(externalDependencyViolation(":auth:data:api", "com.google.dagger", false)!!.contains("ADR-005 rule 3"))
        assertNull(externalDependencyViolation(":auth:data:impl", "com.google.dagger", false))
        assertNull(externalDependencyViolation(":auth:data:api", "javax.inject", false))
    }

    @Test
    fun `ADR-016 rule 1 - Paparazzi only with its plugin`() {
        assertTrue(externalDependencyViolation(":auth:ui:impl", "app.cash.paparazzi", false)!!.contains("ADR-016 rule 1"))
        assertNull(externalDependencyViolation(":auth:ui:impl", "app.cash.paparazzi", true))
    }

    private fun assertViolates(rule: String, module: String, dependency: String, configuration: String = MAIN) {
        val violation = projectDependencyViolation(module, dependency, configuration)
        assertTrue("$module -> $dependency should violate $rule, but was: $violation", violation?.contains("ADR-020 $rule:") == true)
    }

    private fun assertAllowed(module: String, dependency: String, configuration: String = MAIN) {
        assertNull("$module -> $dependency", projectDependencyViolation(module, dependency, configuration))
    }

    private companion object {
        const val MAIN = "implementation"
        const val TEST = "testImplementation"
    }
}
