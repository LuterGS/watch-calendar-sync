package dev.lutergs.watchcalsync.wear.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { WearApp() }
    }
}

@Composable
fun WearApp() {
    MaterialTheme {
        AppScaffold {
            // Placeholder — the agenda list arrives in step 3, once the Data Layer
            // receiver from step 2 is feeding it.
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Cal Sync")
            }
        }
    }
}
