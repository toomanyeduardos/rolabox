package com.eduardoflores.rolabox

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import com.eduardoflores.rolabox.core.designsystem.theme.RolaboxTheme
import com.eduardoflores.rolabox.feature.authentication.AuthenticationScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            RolaboxTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    AuthenticationScreen(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}
