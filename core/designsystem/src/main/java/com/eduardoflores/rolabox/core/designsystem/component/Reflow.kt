package com.eduardoflores.rolabox.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection

/**
 * [Arrangement.SpaceBetween] for a `FlowRow` whose items go to opposite ends of the line while they
 * fit with at least [gap] between them. When they don't, the row wraps and each item starts its own
 * line (ADR-017 rule 5). Plain `SpaceBetween` has no minimum, so two items that only just fit would
 * touch.
 */
internal fun spaceBetween(gap: Dp): Arrangement.Horizontal = object : Arrangement.Horizontal {
    override val spacing = gap

    override fun Density.arrange(
        totalSize: Int,
        sizes: IntArray,
        layoutDirection: LayoutDirection,
        outPositions: IntArray,
    ) = with(Arrangement.SpaceBetween) { arrange(totalSize, sizes, layoutDirection, outPositions) }
}
