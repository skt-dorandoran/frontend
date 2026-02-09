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

class PrivacyCollectionActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CallaiassistantTheme {
                TermsScreen(title = "(필수) 개인정보 수집 및 이용 동의", onBack = { finish() })
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
                    text = "1. 수집 항목\n- 필수: 이름(또는 닉네임), 휴대전화 번호, 연락처 목록, 통화 기록(발신/수신 시간 및 통화 시간), 서비스 설정 값\n- 자동 생성 정보: 기기 모델, OS 버전, 앱 버전, 오류 로그\n\n2. 수집 목적\n- 본인 확인 및 서비스 제공\n- 통화 보조 기능 제공 및 품질 개선\n- 고객 문의 대응 및 공지 전달\n\n3. 보유 및 이용 기간\n- 회원 탈퇴 또는 동의 철회 시까지\n- 관련 법령에 따라 보관이 필요한 정보는 해당 기간 동안 보관\n\n4. 동의 거부 권리\n- 이용자는 개인정보 수집·이용 동의를 거부할 수 있으나, 필수 항목 동의 거부 시 서비스 이용이 제한될 수 있습니다.\n\n5. 개인정보 처리 위탁\n- 서비스 운영을 위해 일부 업무를 위탁할 수 있으며, 위탁 시 관련 법령에 따라 보호 조치를 적용합니다.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
