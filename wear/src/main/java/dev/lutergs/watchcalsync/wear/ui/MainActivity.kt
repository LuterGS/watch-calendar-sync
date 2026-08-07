package dev.lutergs.watchcalsync.wear.ui

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import dev.lutergs.watchcalsync.shared.model.CalendarSnapshot
import dev.lutergs.watchcalsync.wear.data.SnapshotStore

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val store = SnapshotStore.get(applicationContext)
        setContent {
            val snapshot by store.snapshot.collectAsStateWithLifecycle()

            LaunchedEffect(Unit) {
                val loaded = store.load()
                Log.i(TAG, "cache on start: ${loaded?.events?.size ?: "none"} events")
            }

            WearApp(snapshot)
        }
    }

    private companion object {
        const val TAG = "WatchCalSync"
    }
}

@Composable
fun WearApp(snapshot: CalendarSnapshot?) {
    MaterialTheme {
        AppScaffold {
            // Placeholder — the real agenda list lands in step 3. For now this only
            // proves the Data Layer payload reached the watch and survived a restart.
            Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    if (snapshot == null) {
                        Text("동기화 대기 중", textAlign = TextAlign.Center)
                    } else {
                        Text("${snapshot.events.size}건 수신", textAlign = TextAlign.Center)
                        snapshot.events.take(3).forEach {
                            Text(it.title, textAlign = TextAlign.Center)
                        }
                    }
                }
            }
        }
    }
}
