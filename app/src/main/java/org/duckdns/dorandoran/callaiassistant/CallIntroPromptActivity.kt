package org.duckdns.dorandoran.callaiassistant

import android.os.Bundle
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.asPaddingValues
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.imePadding
import org.duckdns.dorandoran.callaiassistant.ui.theme.CallaiassistantTheme
import org.duckdns.dorandoran.callaiassistant.ui.components.FilledSwitch

class CallIntroPromptActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SettingsStore.ensureCallIntroPromptDefaults(this)
        setContent {
            CallaiassistantTheme {
                CallIntroPromptContent(onBack = { finish() })
            }
        }
    }
}

@Composable
private fun CallIntroPromptContent(
    onBack: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var enabled by remember { mutableStateOf(SettingsStore.isCallIntroPromptEnabled(context)) }
    var selectedStyle by remember { mutableStateOf(SettingsStore.getCallIntroPromptStyle(context)) }
    val primaryBlue = Color(0xFF537CEC)
    var customPrompt by remember { mutableStateOf(SettingsStore.getCallIntroPromptCustom(context)) }

    LaunchedEffect(enabled) {
        SettingsStore.setCallIntroPromptEnabled(context, enabled)
    }

    LaunchedEffect(selectedStyle) {
        SettingsStore.setCallIntroPromptStyle(context, selectedStyle)
    }

    // 커스텀 멘트 실시간 저장
    LaunchedEffect(customPrompt) {
        SettingsStore.setCallIntroPromptCustom(context, customPrompt)
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color.White
    ) {
        androidx.compose.foundation.lazy.LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
            horizontalAlignment = Alignment.Start,
            contentPadding = WindowInsets.ime.asPaddingValues()
        ) {
            item {
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
                        text = "통화 시작 안내 멘트",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                }
            }
            item {
                Column(modifier = Modifier.padding(horizontal = 24.dp)) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "통화 시작 안내 멘트 설정",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Medium,
                                fontSize = 16.sp
                            )
                        )
                        FilledSwitch(
                            checked = enabled,
                            onCheckedChange = { enabled = it },
                            checkedTrackColor = primaryBlue,
                            uncheckedTrackColor = Color(0xFFE0E0E0)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "통화 시작 전, 상대방에게 AI 음성 변환 서비스를 사용 중임을 미리 안내하여 원활한 소통을 돕습니다.",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = Color(0xFF888888),
                            fontSize = 13.sp
                        ),
                        lineHeight = 20.sp
                    )

                    if (enabled) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "상세 설정",
                            style = TextStyle(
                                fontSize = 16.sp,
                                lineHeight = 20.8.sp,
                                fontFamily = FontFamily(Font(R.font.pretendard)),
                                fontWeight = FontWeight.Normal,
                                color = Color(0xFF202020)
                            )
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        PromptStyleOption(
                            title = "기본형",
                            description = "\"안녕하세요, 원활한 소통을 위해 AI 음성 변환 서비스를 이용중입니다. 제 말이 조금 늦더라도 양해 부탁드립니다.\"",
                            selected = selectedStyle == CallIntroPromptStyle.BASIC.value,
                            onSelect = { selectedStyle = CallIntroPromptStyle.BASIC.value }
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        PromptStyleOption(
                            title = "상황 설명형",
                            description = "\"안녕하세요. 청각/언어의 어려움으로 텍스트를 음성으로 변환하여 대화하고 있습니다. 천천히 말씀해 주시면 감사하겠습니다.\"",
                            selected = selectedStyle == CallIntroPromptStyle.SITUATION.value,
                            onSelect = { selectedStyle = CallIntroPromptStyle.SITUATION.value }
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        PromptStyleOption(
                            title = "비서형",
                            description = "\"안녕하세요. 지금은 AI 통화 비서가 대화를 돕고 있습니다. 문자로 입력한 내용을 음성으로 전달해 드릴게요.\"",
                            selected = selectedStyle == CallIntroPromptStyle.ASSISTANT.value,
                            onSelect = { selectedStyle = CallIntroPromptStyle.ASSISTANT.value }
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        PromptStyleOption(
                            title = "커스텀",
                            description = if (customPrompt.isBlank()) "직접 입력한 멘트가 여기에 표시됩니다." else customPrompt,
                            selected = selectedStyle == CallIntroPromptStyle.CUSTOM.value,
                            onSelect = { selectedStyle = CallIntroPromptStyle.CUSTOM.value }
                        )
                        if (selectedStyle == CallIntroPromptStyle.CUSTOM.value) {
                            androidx.compose.material3.OutlinedTextField(
                                value = customPrompt,
                                onValueChange = { customPrompt = it },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp),
                                label = { Text("커스텀 안내 멘트 입력") },
                                singleLine = false,
                                maxLines = 3
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PromptStyleOption(
    title: String,
    description: String,
    selected: Boolean,
    onSelect: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.Top
    ) {
        PromptSelectionCircle(
            selected = selected,
            modifier = Modifier.padding(top = 6.dp)
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 8.dp)
        ) {
            Text(
                text = title,
                style = TextStyle(
                    fontSize = 16.sp,
                    lineHeight = 20.8.sp,
                    fontFamily = FontFamily(Font(R.font.pretendard)),
                    fontWeight = FontWeight.Normal,
                    color = Color(0xFF808080)
                )
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = description,
                style = TextStyle(
                    fontSize = 16.sp,
                    lineHeight = 20.8.sp,
                    fontFamily = FontFamily(Font(R.font.pretendard)),
                    fontWeight = FontWeight.Normal,
                    color = Color(0xFF000000)
                )
            )
        }
    }
}

@Composable
private fun PromptSelectionCircle(
    selected: Boolean,
    modifier: Modifier = Modifier
) {
    val ringColor = if (selected) Color(0xFF537CEC) else Color(0xFFD9D9D9)

    Box(
        modifier = modifier
            .size(18.dp)
            .clip(CircleShape)
            .background(Color.White)
            .border(width = 4.5.dp, color = ringColor, shape = CircleShape)
    )
}
