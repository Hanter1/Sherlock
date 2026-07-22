package com.sherlock.bot

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sherlock.bot.data.ScanNotifier
import com.sherlock.bot.ui.theme.Cabinet
import com.sherlock.bot.ui.theme.SherlockTheme
import com.sherlock.bot.ui.workbench.WorkbenchScreen
import com.sherlock.bot.ui.workbench.WorkbenchViewModel

class MainActivity : ComponentActivity() {

    private var workbenchViewModel: WorkbenchViewModel? = null

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* optional */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        ScanNotifier.ensureChannel(this)
        maybeRequestNotificationPermission()
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                workbenchViewModel?.setAppInForeground(true)
            }

            override fun onStop(owner: LifecycleOwner) {
                workbenchViewModel?.setAppInForeground(false)
            }
        })
        setContent {
            SherlockTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Cabinet.Bg,
                ) {
                    val viewModel: WorkbenchViewModel = viewModel(
                        factory = WorkbenchViewModel.Factory(application),
                    )
                    workbenchViewModel = viewModel
                    WorkbenchScreen(viewModel = viewModel)
                }
            }
        }
    }

    private fun maybeRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
