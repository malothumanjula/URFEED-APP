package com.urfeed.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.urfeed.app.feature.auth.AuthViewModel
import com.urfeed.app.navigation.URFeedNavHost
import com.urfeed.app.ui.theme.URFeedTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            URFeedTheme {
                URFeedApp()
            }
        }
    }
}

@Composable
fun URFeedApp() {
    val context = LocalContext.current
    val authViewModel: AuthViewModel = viewModel(factory = AuthViewModel.Factory(context.applicationContext))
    URFeedNavHost(authViewModel = authViewModel)
}
