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

class PrivacyThirdPartyActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CallaiassistantTheme {
                TermsScreen(title = "(필수) 개인정보 제3자 제공 동의", onBack = { finish() })
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
                    text = "1. 제공받는 자\n- 통화 음성·텍스트 처리 협력사(국내/외 클라우드 또는 AI 처리 사업자)\n\n2. 제공 목적\n- 통화 음성의 텍스트 변환 및 요약·분석 기능 제공\n- 서비스 품질 개선 및 오류 분석\n\n3. 제공 항목\n- 통화 음성 데이터, 변환된 텍스트, 통화 시간 정보, 기기·앱 기본 정보\n\n4. 보유 및 이용 기간\n- 제공 목적 달성 후 지체 없이 삭제\n- 법령에 의해 보존이 필요한 경우 해당 기간 동안 보관\n\n5. 동의 거부 권리\n- 이용자는 제3자 제공 동의를 거부할 수 있으나, 거부 시 관련 기능 이용이 제한될 수 있습니다.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
