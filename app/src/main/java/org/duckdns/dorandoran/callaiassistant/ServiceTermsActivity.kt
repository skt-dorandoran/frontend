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

class ServiceTermsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CallaiassistantTheme {
                TermsScreen(title = "(필수) 서비스 이용 약관 동의", onBack = { finish() })
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
                    text = "제1조 (목적)\n본 약관은 T.mate(이하 \"서비스\")의 이용 조건과 절차, 이용자와 회사의 권리·의무 및 책임사항을 규정합니다.\n\n제2조 (이용 자격)\n서비스는 합법적인 목적의 통화 보조 기능을 제공하며, 만 14세 미만은 법정대리인의 동의가 필요합니다.\n\n제3조 (서비스 제공)\n회사는 통화 보조, 연락처 연동, 통화 기록 관리 등 기능을 제공합니다. 기능은 운영상 필요에 따라 변경될 수 있습니다.\n\n제4조 (이용자의 의무)\n이용자는 다음 행위를 해서는 안 됩니다.\n1) 타인의 권리를 침해하는 행위\n2) 불법 통화 녹취·도청 등 관계 법령을 위반하는 행위\n3) 서비스의 정상 운영을 방해하는 행위\n\n제5조 (서비스 변경 및 중단)\n회사는 운영, 보안, 기술적 사유가 있을 경우 사전 고지 후 서비스의 전부 또는 일부를 변경·중단할 수 있습니다.\n\n제6조 (책임의 제한)\n회사는 천재지변, 통신 장애 등 불가항력으로 인한 손해에 대해 책임을 지지 않습니다.\n\n제7조 (문의)\n서비스 관련 문의는 앱 내 고객센터를 통해 접수합니다.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
