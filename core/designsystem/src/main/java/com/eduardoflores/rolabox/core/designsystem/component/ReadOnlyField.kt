package com.eduardoflores.rolabox.core.designsystem.component

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.eduardoflores.rolabox.core.designsystem.theme.RolaboxMetal
import com.eduardoflores.rolabox.core.designsystem.theme.RolaboxTheme
import com.eduardoflores.rolabox.core.designsystem.theme.RolaboxType

private val ReadOnlyShape = RoundedCornerShape(12.dp)

/**
 * A labelled value that can't be edited, such as the address a link was sent to. It's outlined, not
 * recessed. A value that doesn't fit on one line wraps, so it can be read in full (ADR-017).
 */
@Composable
fun ReadOnlyField(label: String, value: String, modifier: Modifier = Modifier) {
    val styles = RolaboxType.styles
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(text = label, style = styles.fieldLabel)
        Box(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 50.dp)
                .border(1.dp, RolaboxMetal.colors.rule, ReadOnlyShape)
                .padding(horizontal = 14.dp, vertical = 8.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(text = value, style = styles.fieldInput)
        }
    }
}

@PreviewLightDark
@Composable
private fun ReadOnlyFieldPreview() {
    RolaboxTheme {
        Column(Modifier.brushedMetal().padding(22.dp)) {
            ReadOnlyField(label = "Sent to", value = "toomanyeduardos@gmail.com")
        }
    }
}
