package org.duckdns.dorandoran.callaiassistant

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
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

@OptIn(ExperimentalMaterial3Api::class)
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
    val scrollState = rememberScrollState()

    // 글자 크기 단계에 따른 미리보기 폰트 사이즈 계산
    val previewFontSize = remember(textSizeStep) {
        when (textSizeStep.roundToInt()) {
            0 -> 14.sp
            1 -> 17.sp
            else -> 22.sp // 2단계일 때 확실히 커 보이도록 설정
        }
    }

    // 줄 간격도 글자 크기에 맞춰 자연스럽게 조정
    val previewLineHeight = remember(textSizeStep) {
        when (textSizeStep.roundToInt()) {
            0 -> 22.sp
            1 -> 26.sp
            else -> 32.sp
        }
    }

    LaunchedEffect(textSizeStep) {
        SettingsStore.setCallTextSizeStep(context, textSizeStep.toInt())
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color.White
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.Top,
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
                    onClick = onBack,
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
                    text = "설정",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                )
            }

            Column(
                modifier = Modifier.padding(horizontal = 24.dp)
            ) {
                Spacer(modifier = Modifier.height(24.dp))

                // --- Text Size Slider Section ---
                Text(
                    text = "통화 중 글자 크기",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp
                    )
                )
                Spacer(modifier = Modifier.height(8.dp))

                // [수정됨] 설명 텍스트: 슬라이더 값에 따라 크기(previewFontSize)가 변경됨
                Text(
                    text = "통화 중 입력하는 텍스트를 보기 편한 크기로 설정해요",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = Color(0xFF888888),
                        fontSize = previewFontSize // 동적 폰트 크기 적용
                    ),
                    lineHeight = previewLineHeight
                )

                Spacer(modifier = Modifier.height(10.dp))

                // 슬라이더 라벨 (A ... A)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "A", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Color.Black)
                    Text(text = "A", fontSize = 20.sp, fontWeight = FontWeight.Medium, color = Color.Black)
                }

                Spacer(modifier = Modifier.height(8.dp))

                // [수정됨] 슬라이더: 트랙에 세로 구분선(Tick) 추가
                Slider(
                    value = textSizeStep,
                    onValueChange = { textSizeStep = it },
                    onValueChangeFinished = {
                        textSizeStep = textSizeStep.roundToInt().toFloat()
                    },
                    steps = 1,
                    valueRange = 0f..2f,
                    modifier = Modifier.fillMaxWidth(),
                    // 1. 커스텀 썸 (손잡이)
                    thumb = {
                        Surface(
                            shape = CircleShape,
                            color = Color.White,
                            modifier = Modifier
                                .size(28.dp)
                                .shadow(elevation = 3.dp, shape = CircleShape)
                        ) {}
                    },
                    // 2. 커스텀 트랙 + 세로 막대기(Tick) 구현
                    track = { sliderState ->
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            // (1) 가로 얇은 선 (Rail)
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(2.dp)
                                    .background(Color(0xFFE0E0E0)) // 연한 회색 트랙
                            )

                            // (2) 세로 구분선 (Ticks) - 양쪽 끝과 가운데
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                // 시작점 Tick
                                Box(
                                    modifier = Modifier
                                        .width(2.dp)
                                        .height(10.dp) // 트랙보다 길게 설정
                                        .background(Color(0xFFE0E0E0))
                                )
                                // 중간점 Tick
                                Box(
                                    modifier = Modifier
                                        .width(2.dp)
                                        .height(10.dp)
                                        .background(Color(0xFFE0E0E0))
                                )
                                // 끝점 Tick
                                Box(
                                    modifier = Modifier
                                        .width(2.dp)
                                        .height(10.dp)
                                        .background(Color(0xFFE0E0E0))
                                )
                            }
                        }
                    }
                )

                Spacer(modifier = Modifier.height(24.dp))

                SettingsDivider()

                // --- Menu Items ---
                SettingsMenuRow(
                    title = "음성 클론",
                    description = "미리 등록한 목소리로 통화 중 입력한 문장을 읽어드려요",
                    onClick = onOpenVoiceClone
                )
                SettingsDivider()

                SettingsMenuRow(
                    title = "내 번호 설정",
                    description = "상대방에게 표시될 내 전화번호를 입력해요",
                    onClick = onOpenMyPhoneNumber
                )
                SettingsDivider()

                SettingsMenuRow(
                    title = "통화 필수 권한",
                    description = null,
                    onClick = onOpenPermissions
                )
                SettingsDivider()

                SettingsMenuRow(
                    title = "통화 시작 안내 멘트",
                    description = "상대방이 전화를 받으면 AI 통화 중임을 알리는 멘트를 먼저 송출해요",
                    onClick = onOpenCallIntroPrompt
                )
            }
        }
    }
}

@Composable
private fun SettingsDivider() {
    HorizontalDivider(
        thickness = 1.dp,
        color = Color(0xFFF0F0F0),
        modifier = Modifier.padding(horizontal = 0.dp)
    )
}

@Composable
private fun SettingsMenuRow(
    title: String,
    description: String?,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                onClick = onClick,
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            )
            .padding(vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = Color.Black
                )
            )
            if (description != null) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = Color(0xFF999999),
                        fontSize = 13.sp
                    ),
                    lineHeight = 19.sp
                )
            }
        }

        Spacer(modifier = Modifier.width(16.dp))

        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = Color(0xFFD0D0D0),
            modifier = Modifier.size(24.dp)
        )
    }
}