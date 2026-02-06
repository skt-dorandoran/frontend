package org.duckdns.dorandoran.callaiassistant

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.duckdns.dorandoran.callaiassistant.ui.theme.CallaiassistantTheme

class CallTextProcessingActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CallaiassistantTheme {
                TermsScreen(title = "(필수) 통화 내용 텍스트 변환 및 처리 동의", onBack = { finish() })
            }
        }
    }
}

@Composable
private fun TermsScreen(
    title: String,
    onBack: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(24.dp),
            verticalArrangement = Arrangement.Top,
            horizontalAlignment = Alignment.Start
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "뒤로가기"
                    )
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "1. 처리 목적\n- 통화 음성을 텍스트로 변환하여 화면에 표시\n- 통화 요약 및 맥락 제공 기능 지원\n\n2. 처리 항목\n- 통화 음성 데이터(수신/발신 음성)\n- 변환된 텍스트 및 통화 시간 정보\n\n3. 처리 방식\n- 실시간 또는 통화 후 자동 변환\n- 서비스 품질 향상을 위한 비식별 통계 생성\n\n4. 보관 및 삭제\n- 변환 결과는 이용자가 확인할 수 있도록 일정 기간 보관될 수 있습니다.\n- 이용자가 삭제를 요청하면 즉시 또는 합리적인 기간 내 삭제합니다.\n\n5. 동의 거부 권리\n- 본 동의를 거부할 수 있으나, 통화 텍스트 변환 기능은 제공되지 않습니다.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
