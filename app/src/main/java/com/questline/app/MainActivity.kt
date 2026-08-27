package com.questline.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.questline.app.ui.QuestlineApp
import com.questline.app.ui.theme.AppTheme
import com.questline.app.ui.theme.QuestlineTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        AppTheme.load(this)
        setContent {
            QuestlineTheme {
                QuestlineApp()
            }
        }
    }
}
