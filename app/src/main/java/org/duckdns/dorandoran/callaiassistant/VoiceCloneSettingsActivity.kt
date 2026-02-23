package org.duckdns.dorandoran.callaiassistant

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import kotlinx.coroutines.flow.MutableStateFlow
import androidx.compose.runtime.collectAsState
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.duckdns.dorandoran.callaiassistant.ui.theme.CallaiassistantTheme
import org.duckdns.dorandoran.callaiassistant.voiceclone.VoiceCloneApi
import org.duckdns.dorandoran.callaiassistant.voiceclone.VoiceCloneStore
import org.duckdns.dorandoran.callaiassistant.voiceclone.VoiceCloneNotification
import org.duckdns.dorandoran.callaiassistant.VoiceTrainingActivity
import android.widget.Toast
import androidx.compose.material3.CircularProgressIndicator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

import androidx.compose.runtime.rememberCoroutineScope
import org.duckdns.dorandoran.callaiassistant.ui.components.FilledSwitch

class VoiceCloneSettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CallaiassistantTheme {
                VoiceCloneSettingsContent(onBack = { finish() })
            }
        }
    }
}

@Composable
private fun VoiceCloneSettingsContent(onBack: () -> Unit) {
    val context = LocalContext.current
    val activity = context as? Activity
    var isVoiceCloneEnabled by remember { mutableStateOf(SettingsStore.isVoiceCloneEnabled(context)) }
    val voiceIdFlow = remember { MutableStateFlow(VoiceCloneStore.getVoiceId(context)) }

    // 액티비티 재진입 시 voiceId 강제 갱신
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                voiceIdFlow.value = VoiceCloneStore.getVoiceId(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val voiceId by voiceIdFlow.collectAsState()
    val isVoiceTrained = voiceId != null
    var isDeleteButtonVisible by remember { mutableStateOf(true) }
    var isModelCreating by remember { mutableStateOf(false) }
    var modelCreateError by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()
    val currentEnabled by rememberUpdatedState(isVoiceCloneEnabled)
    val currentTrained by rememberUpdatedState(isVoiceTrained)

    // 시안의 파란색 (약간의 보라빛이 도는 파랑)
    val primaryBlue = Color(0xFF537CEC)

    LaunchedEffect(isVoiceCloneEnabled) {
        SettingsStore.setVoiceCloneEnabled(context, isVoiceCloneEnabled)
    }

    DisposableEffect(Unit) {
        onDispose {
            if (activity?.isFinishing == true && currentEnabled && !currentTrained) {
                SettingsStore.setVoiceCloneEnabled(context, false)
            }
        }
    }

    fun handleBack() {
        if (isVoiceCloneEnabled && !isVoiceTrained) {
            isVoiceCloneEnabled = false
            SettingsStore.setVoiceCloneEnabled(context, false)
        }
        onBack()
    }

    BackHandler {
        handleBack()
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color.White
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
            horizontalAlignment = Alignment.Start
        ) {
            // --- Header ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { handleBack() },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                        contentDescription = "뒤로가기",
                        tint = Color.Black
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "음성 클론",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.SemiBold
                    )
                )
            }

            // --- Main Content ---
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp)
            ) {
                Spacer(modifier = Modifier.height(24.dp))

                // Switch Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "음성 클론 설정",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Medium,
                            fontSize = 16.sp
                        )
                    )
                    FilledSwitch(
                        checked = isVoiceCloneEnabled,
                        onCheckedChange = { checked ->
                            isVoiceCloneEnabled = checked
                        },
                        checkedTrackColor = primaryBlue,
                        uncheckedTrackColor = Color(0xFFE0E0E0)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Description Text
                Text(
                    text = "활성화하면 통화 중에 입력한 문장을 AI가 내 목소리로 대신전달해요",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = Color(0xFF888888),
                        fontSize = 14.sp
                    ),
                    lineHeight = 20.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "처음 한 번, AI 학습을 위해 약 10초 정도 음성 녹음이 필요해요",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = Color(0xFF888888),
                        fontSize = 14.sp
                    ),
                    lineHeight = 20.sp
                )

                Spacer(modifier = Modifier.height(40.dp))

                // Action Button Section
                if (isVoiceCloneEnabled) {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isModelCreating) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator()
                                Text("음성 클론 모델 생성중입니다", color = Color.Gray)
                                Spacer(modifier = Modifier.height(8.dp))
                                if (modelCreateError != null) {
                                    Text(modelCreateError!!, color = Color.Red)
                                }
                            }
                        }
                        // 버튼 표시 로직: voiceId 없으면 학습 시작만, 있으면 둘 다
                        if (!isVoiceTrained) {
                            Button(
                                onClick = {
                                    val intent = Intent(context, VoiceTrainingActivity::class.java)
                                    context.startActivity(intent)
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = primaryBlue,
                                    contentColor = Color.White
                                ),
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(50),
                                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp)
                            ) {
                                Text(
                                    text = "내 목소리 학습 시작",
                                    style = MaterialTheme.typography.labelLarge.copy(
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        } else {
                            // voiceId 있으면 두 버튼 모두
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Button(
                                    onClick = {
                                        val intent = Intent(context, VoiceTrainingActivity::class.java)
                                        context.startActivity(intent)
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFF537CEC), // 파란색
                                        contentColor = Color.White
                                    ),
                                    shape = androidx.compose.foundation.shape.RoundedCornerShape(50),
                                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = "내 목소리 다시 학습",
                                        style = MaterialTheme.typography.labelLarge.copy(
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(12.dp))
                                var showDeleteDialog by remember { mutableStateOf(false) }
                                if (isDeleteButtonVisible) {
                                    Button(
                                        onClick = { showDeleteDialog = true },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Color(0xFFD32F2F),
                                            contentColor = Color.White
                                        ),
                                        shape = androidx.compose.foundation.shape.RoundedCornerShape(50),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = "음성 클론 모델 삭제",
                                            style = MaterialTheme.typography.labelLarge.copy(
                                                fontSize = 16.sp,
                                                fontWeight = FontWeight.Medium
                                            )
                                        )
                                    }
                                }
                                if (showDeleteDialog) {
                                    AlertDialog(
                                        onDismissRequest = { showDeleteDialog = false },
                                        title = { Text("음성 클론 모델 삭제") },
                                        text = { Text("정말로 음성 클론 모델을 삭제하시겠습니까?") },
                                        confirmButton = {
                                            Button(onClick = {
                                                showDeleteDialog = false
                                                isDeleteButtonVisible = false
                                                val voiceIdLocal = VoiceCloneStore.getVoiceId(context)
                                                if (voiceIdLocal != null) {
                                                    coroutineScope.launch {
                                                        val result = withContext(Dispatchers.IO) {
                                                            VoiceCloneApi.deleteVoiceClone(voiceIdLocal)
                                                        }
                                                        if (result) {
                                                            VoiceCloneStore.clearVoiceId(context)
                                                            voiceIdFlow.value = null
                                                            Toast.makeText(context, "음성 클론 모델이 삭제되었습니다.", Toast.LENGTH_SHORT).show()
                                                        } else {
                                                            Toast.makeText(context, "모델 삭제 실패", Toast.LENGTH_SHORT).show()
                                                        }
                                                    }
                                                }
                                            }) { Text("확인") }
                                        },
                                        dismissButton = {
                                            Button(onClick = { showDeleteDialog = false }) { Text("취소") }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
