package com.eduardoflores.rolabox.feature.account

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.eduardoflores.rolabox.core.designsystem.theme.RolaboxTheme

@Composable
fun AuthenticationScreen(modifier: Modifier = Modifier) {
    Text(
        text = "Hello Android!",
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
private fun AuthenticationScreenPreview() {
    RolaboxTheme {
        AuthenticationScreen()
    }
}
