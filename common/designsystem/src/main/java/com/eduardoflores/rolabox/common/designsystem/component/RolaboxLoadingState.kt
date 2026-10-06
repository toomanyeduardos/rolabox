package com.eduardoflores.rolabox.common.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.eduardoflores.rolabox.common.designsystem.theme.RolaboxTheme
import com.eduardoflores.rolabox.common.designsystem.theme.RolaboxType

/** Full-area loading indicator, with an optional message underneath. */
@Composable
fun RolaboxLoadingState(modifier: Modifier = Modifier, message: String? = null) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator(
            modifier = Modifier.semantics { contentDescription = message ?: "Loading" },
        )
        if (message != null) {
            Text(text = message, style = RolaboxType.styles.message, textAlign = TextAlign.Center)
        }
    }
}

@PreviewLightDark
@Composable
private fun RolaboxLoadingStatePreview() {
    RolaboxTheme {
        Surface {
            RolaboxLoadingState(message = "Loading your library…")
        }
    }
}
