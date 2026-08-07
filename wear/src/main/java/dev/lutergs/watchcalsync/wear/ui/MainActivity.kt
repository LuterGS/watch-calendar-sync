package dev.lutergs.watchcalsync.wear.ui

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.TimeText
import dev.lutergs.watchcalsync.shared.model.CalendarSnapshot
import dev.lutergs.watchcalsync.wear.data.SnapshotStore

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val store = SnapshotStore.get(applicationContext)
        setContent {
            // Reading the store's StateFlow means a sync arriving while the screen is
            // open re-renders the agenda without any manual refresh.
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
        AppScaffold(timeText = { TimeText() }) {
            AgendaScreen(snapshot)
        }
    }
}
