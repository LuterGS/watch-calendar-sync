package dev.lutergs.watchcalsync.wear.ui

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.TimeText
import dev.lutergs.watchcalsync.shared.model.CalendarSnapshot
import dev.lutergs.watchcalsync.wear.data.SnapshotStore
import dev.lutergs.watchcalsync.wear.sync.RequestResult
import dev.lutergs.watchcalsync.wear.sync.SyncRequester
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val store = SnapshotStore.get(applicationContext)
        setContent {
            // Reading the store's StateFlow means a sync arriving while the screen is
            // open updates the agenda in place, including one this screen requested.
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
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val requester = remember(context) { SyncRequester(context) }
    var refreshing by remember { mutableStateOf(false) }

    MaterialTheme {
        AppScaffold(timeText = { TimeText() }) {
            AgendaScreen(
                snapshot = snapshot,
                refreshing = refreshing,
                onRefresh = {
                    scope.launch {
                        refreshing = true
                        // The phone pushes the result back over the Data Layer, so
                        // there is nothing to await here — the snapshot flow updates
                        // the list when it lands.
                        val message = when (val result = requester.requestSync()) {
                            RequestResult.Sent -> "폰에 동기화 요청함"
                            RequestResult.NoPhone -> "폰이 연결되지 않음"
                            is RequestResult.Failed -> "요청 실패: ${result.message}"
                        }
                        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                        refreshing = false
                    }
                },
            )
        }
    }
}
