package com.eduardoflores.rolabox.device.settings.impl

import com.eduardoflores.rolabox.device.settings.api.SettingsSection
import com.eduardoflores.rolabox.device.settings.api.SettingsSlot

/**
 * The contributed [sections] in the order of their slots, which is the order of the settings list
 * (ADR-020, rule 16). A slot nobody fills is left out.
 *
 * Two sections in one slot is a mistake in the graph, and fails here, when the settings entries are
 * created, with the names of both.
 */
internal fun arrangeSections(sections: Set<SettingsSection>): List<SettingsSection> {
    val bySlot = sections.groupBy { it.slot }
    return SettingsSlot.entries.mapNotNull { slot ->
        val filling = bySlot[slot].orEmpty()
        check(filling.size <= 1) {
            "The $slot slot of the settings list is filled by ${filling.map { it::class.simpleName }}. " +
                "ADR-020 rule 16: a slot has at most one section."
        }
        filling.singleOrNull()
    }
}
