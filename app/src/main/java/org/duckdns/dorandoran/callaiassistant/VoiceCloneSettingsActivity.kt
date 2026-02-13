package org.duckdns.dorandoran.callaiassistant

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.duckdns.dorandoran.callaiassistant.ui.theme.CallaiassistantTheme

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
    var isVoiceTrained by remember { mutableStateOf(false) } // 실제 앱 로직에 따라 초기값 연동 필요
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
                                // 둥근 알약 모양 (Pill Shape)
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
                            // 이미 학습된 경우 (디자인 시안에는 없지만 기능 유지를 위해 스타일만 통일)
                            Button(
                                onClick = { },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFFF0F0F0),
                                    contentColor = Color.Black
                                ),
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(50),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "내 목소리 변경하기",
                                    style = MaterialTheme.typography.labelLarge.copy(
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FilledSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    checkedTrackColor: Color,
    uncheckedTrackColor: Color,
    modifier: Modifier = Modifier,
    thumbColor: Color = Color.White
) {
    val switchWidth = 52.dp
    val switchHeight = 32.dp
    val thumbSize = 24.dp
    val padding = 4.dp
    val trackColor by animateColorAsState(
        targetValue = if (checked) checkedTrackColor else uncheckedTrackColor,
        label = "SwitchTrackColor"
    )
    val thumbOffset by animateDpAsState(
        targetValue = if (checked) switchWidth - thumbSize - (padding * 2) else 0.dp,
        label = "SwitchThumbOffset"
    )

    Box(
        modifier = modifier
            .size(width = switchWidth, height = switchHeight)
            .clip(CircleShape)
            .background(trackColor)
            .toggleable(
                value = checked,
                role = Role.Switch,
                onValueChange = onCheckedChange
            ),
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = padding),
            contentAlignment = Alignment.CenterStart
        ) {
            Box(
                modifier = Modifier
                    .offset(x = thumbOffset)
                    .size(thumbSize)
                    .background(thumbColor, CircleShape)
            )
        }
    }
}