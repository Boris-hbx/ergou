package com.ergou.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.ergou.app.ui.navigation.ErgouNavigation
import com.ergou.app.ui.theme.ErgouTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ErgouTheme {
                ErgouNavigation()
            }
        }
    }
}
