package com.sherlock.bot

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sherlock.bot.ui.workbench.WorkbenchViewModel
import com.sherlock.bot.ui.theme.Cabinet
import com.sherlock.bot.ui.theme.SherlockTheme
import com.sherlock.bot.ui.workbench.WorkbenchScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SherlockTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Cabinet.Bg,
                ) {
                    val viewModel: WorkbenchViewModel = viewModel(
                        factory = WorkbenchViewModel.Factory(application),
                    )
                    WorkbenchScreen(viewModel = viewModel)
                }
            }
        }
    }
}
