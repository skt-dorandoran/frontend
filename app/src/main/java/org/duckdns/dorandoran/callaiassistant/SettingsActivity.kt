package org.duckdns.dorandoran.callaiassistant

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.duckdns.dorandoran.callaiassistant.ui.theme.CallaiassistantTheme
import kotlin.math.roundToInt

class SettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CallaiassistantTheme {
                SettingsContent(
                    onOpenVoiceClone = {
                        startActivity(Intent(this, VoiceCloneSettingsActivity::class.java))
                    },
                    onOpenMyPhoneNumber = {
                        startActivity(Intent(this, MyPhoneNumberActivity::class.java))
                    },
                    onOpenPermissions = {
                        startActivity(Intent(this, CallPermissionsActivity::class.java))
                    },
                    onOpenCallIntroPrompt = {
                        startActivity(Intent(this, CallIntroPromptActivity::class.java))
                    },
                    onBack = { finish() }
                )
            }
        }
    }
}

@Composable
private fun SettingsContent(
    onOpenVoiceClone: () -> Unit,
    onOpenMyPhoneNumber: () -> Unit,
    onOpenPermissions: () -> Unit,
    onOpenCallIntroPrompt: () -> Unit,
    onBack: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var textSizeStep by remember { mutableFloatStateOf(SettingsStore.getCallTextSizeStep(context).toFloat()) }
    LaunchedEffect(textSizeStep) {
        SettingsStore.setCallTextSizeStep(context, textSizeStep.toInt())
    }

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
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Start
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "뒤로가기"
                    )
                }
                Text(
                    text = "설정",
                    style = MaterialTheme.typography.headlineSmall
                )
            }
            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "통화 중 글자 크기",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "통화 중 입력하는 텍스트를 보기 편한 크기로 설정해요.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = "작게", fontSize = 12.sp)
                Text(text = "보통", fontSize = 14.sp)
                Text(text = "크게", fontSize = 16.sp)
            }
            Spacer(modifier = Modifier.height(6.dp))
            Slider(
                value = textSizeStep,
                onValueChange = { value ->
                    textSizeStep = value
                },
                onValueChangeFinished = {
                    textSizeStep = textSizeStep.roundToInt().toFloat()
                },
                steps = 1,
                valueRange = 0f..2f
            )

            Spacer(modifier = Modifier.height(24.dp))
            Divider()
            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpenVoiceClone)
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(text = "음성 클론", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "미리 등록한 목소리로 통화 중 입력한 문장을 읽어드려요",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Text(text = ">", style = MaterialTheme.typography.bodyMedium)
            }

            Divider()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpenMyPhoneNumber)
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(text = "내 번호 설정", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "상대방에게 표시될 내 전화번호를 입력해요",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(text = ">", style = MaterialTheme.typography.bodyMedium)
            }

            Divider()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpenPermissions)
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = "통화 필수 권한", style = MaterialTheme.typography.titleMedium)
                Text(text = ">", style = MaterialTheme.typography.bodyMedium)
            }

            Divider()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpenCallIntroPrompt)
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(text = "통화 시작 안내 멘트", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "상대방이 전화를 받으면 AI 통화 중임을 알리는 멘트를 먼저 송출해요",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(text = ">", style = MaterialTheme.typography.bodyMedium)
            }

        }
    }
}
