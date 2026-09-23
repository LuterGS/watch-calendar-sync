package dev.lutergs.watchcalsync.wear.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import dev.lutergs.watchcalsync.wear.settings.AgendaFontSize
import dev.lutergs.watchcalsync.wear.settings.EventMode

@Composable
fun SettingsScreen(
    mode: EventMode,
    fontSize: AgendaFontSize,
    onModeChange: (EventMode) -> Unit,
    onFontSizeChange: (AgendaFontSize) -> Unit,
    onClose: () -> Unit,
) {
    val state = rememberTransformingLazyColumnState()
    ScreenScaffold(scrollState = state, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 32.dp)) { padding ->
        TransformingLazyColumn(
            state = state,
            contentPadding = padding,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            item { ListHeader { Text("컴플리케이션 일정") } }
            item { Text("모든 컴플리케이션에 적용됩니다") }
            EventMode.entries.forEach { option ->
                item {
                    Button(onClick = { onModeChange(option) }, modifier = Modifier.fillMaxWidth()) {
                        Text(if (mode == option) "✓ ${option.label}" else option.label)
                    }
                }
            }
            item { Text("다가오는 일정: 시작 전인 시간 지정 일정\n종일 일정: 오늘의 종일 일정") }
            item { ListHeader { Text("앱 글자 크기") } }
            AgendaFontSize.entries.forEach { option ->
                item {
                    Button(onClick = { onFontSizeChange(option) }, modifier = Modifier.fillMaxWidth()) {
                        Text(if (fontSize == option) "✓ ${option.label}" else option.label)
                    }
                }
            }
            item { Text("14:00 주간회의", fontSize = (16f * fontSize.scale).sp) }
            item { Text("컴플리케이션 글자 크기는 워치페이스가 크기 설정을 지원하는 경우에만 바꿀 수 있습니다.") }
            item { Button(onClick = onClose, modifier = Modifier.fillMaxWidth()) { Text("완료") } }
        }
    }
}
