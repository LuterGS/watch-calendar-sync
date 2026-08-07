package dev.lutergs.watchcalsync.mobile.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MaterialTheme {
                val context = LocalContext.current
                val vm: MainViewModel = viewModel()
                val state by vm.uiState.collectAsStateWithLifecycle()

                val permissionLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission()
                ) { granted -> vm.onPermissionResult(granted) }

                LaunchedEffect(Unit) {
                    val granted = ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.READ_CALENDAR,
                    ) == PackageManager.PERMISSION_GRANTED

                    if (granted) {
                        vm.onPermissionResult(true)
                    } else {
                        permissionLauncher.launch(Manifest.permission.READ_CALENDAR)
                    }
                }

                Surface(
                    modifier = Modifier,
                    color = MaterialTheme.colorScheme.background,
                ) {
                    MainScreen(
                        state = state,
                        onRefresh = vm::refresh,
                        onRequestPermission = {
                            permissionLauncher.launch(Manifest.permission.READ_CALENDAR)
                        },
                        onCalendarEnabledChange = vm::setCalendarEnabled,
                        onCalendarsEnabledChange = vm::setCalendarsEnabled,
                    )
                }
            }
        }
    }
}
