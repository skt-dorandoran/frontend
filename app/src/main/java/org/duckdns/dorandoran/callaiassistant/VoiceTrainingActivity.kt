package org.duckdns.dorandoran.callaiassistant

import android.media.MediaPlayer
import org.duckdns.dorandoran.callaiassistant.voiceclone.WavUtil
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.graphics.graphicsLayer

import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import kotlinx.coroutines.delay
import org.duckdns.dorandoran.callaiassistant.ui.theme.CallaiassistantTheme
import androidx.compose.ui.platform.LocalContext
import java.io.File
import org.duckdns.dorandoran.callaiassistant.voiceclone.VoiceRecorder
import org.duckdns.dorandoran.callaiassistant.voiceclone.AudioMergeUtil


class VoiceTrainingActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        setContent {
            CallaiassistantTheme {
                VoiceTrainingScreen(onClose = { finish() })
            }
        }
    }
}

@Composable
private fun VoiceTrainingScreen(onClose: () -> Unit) {
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    // 학습 상태 관리 변수 추가
    var isTrainingStarted by remember { mutableStateOf(false) }
    var trainingStep by remember { mutableStateOf(0) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    val voiceIdFlow = remember { MutableStateFlow(org.duckdns.dorandoran.callaiassistant.voiceclone.VoiceCloneStore.getVoiceId(context)) }
    val voiceIdState by voiceIdFlow.collectAsState()
        // 4단계: ffmpeg 합치기/재생 관련 상태
        var isMerged by remember { mutableStateOf(false) }
        var isPlaying by remember { mutableStateOf(false) }
        val mergedFile = File(context.cacheDir, "voice_merged.m4a")
        val audioFiles = listOf(
            File(context.cacheDir, "voice_step1.m4a"),
            File(context.cacheDir, "voice_step2.m4a"),
            File(context.cacheDir, "voice_step3.m4a")
        )
        var audioPlayer: MediaPlayer? by remember { mutableStateOf(null) }
    val pulseTransition = rememberInfiniteTransition(label = "voiceTrainingPulse")
    val pulseScale by pulseTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.16f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 800),
            repeatMode = RepeatMode.Reverse
        ),
        label = "voiceTrainingPulseScale"
    )
    val pulseAlpha by pulseTransition.animateFloat(
        initialValue = 0.22f,
        targetValue = 0.48f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 800),
            repeatMode = RepeatMode.Reverse
        ),
        label = "voiceTrainingPulseAlpha"
    )

    // 녹음 상태 관리
    var isRecording by remember { mutableStateOf(false) }
    var recordedFilePath by remember { mutableStateOf<String?>(null) }
    val recorder = remember { mutableStateOf<org.duckdns.dorandoran.callaiassistant.voiceclone.VoiceRecorder?>(null) }

    val boxModifier = if (isTrainingStarted && trainingStep < 4) {
        Modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    colorStops = arrayOf(
                        0.0f to ComposeColor(0xCCA371FE),
                        0.58f to ComposeColor(0xFF74A5FA),
                        1.0f to ComposeColor(0xFF74A5FA)
                    )
                )
            )
    } else {
        Modifier
            .fillMaxSize()
            .background(
                ComposeColor.White
            )
    }

    Box(
        modifier = boxModifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 상단: 진행 점(중앙) + X 닫기 버튼(우측)
            var isCloseVisible by remember { mutableStateOf(true) }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, start = 12.dp, end = 12.dp)
            ) {
                if (isTrainingStarted && trainingStep in 1..3) {
                    Row(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        repeat(3) { index ->
                            val active = index <= (trainingStep - 1)
                            Box(
                                modifier = Modifier
                                    .size(11.dp)
                                    .background(
                                        color = if (active) ComposeColor.White else ComposeColor.White.copy(alpha = 0f),
                                        shape = CircleShape
                                    )
                                    .border(
                                        width = if (active) 0.dp else 2.dp,
                                        color = ComposeColor.White,
                                        shape = CircleShape
                                    )
                            )
                        }
                    }
                }
                // 4페이지에서는 학습 완료(voiceIdState != null)일 때만 닫기 버튼 visible
                if (isTrainingStarted && trainingStep == 4) {
                    isCloseVisible = voiceIdState != null
                } else {
                    isCloseVisible = true
                }
                IconButton(
                    onClick = onClose,
                    enabled = isCloseVisible,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .graphicsLayer { alpha = if (isCloseVisible) 1f else 0f }
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "닫기",
                        tint = ComposeColor.Black,
                        modifier = Modifier.size(30.dp)
                    )
                }
            }

            if (!isTrainingStarted) {
                Spacer(modifier = Modifier.weight(0.35f))

                // 그라데이션 원형 (보라 → 민트, 좌하 → 우상)
                Box(
                    modifier = Modifier
                        .size(290.dp)
                        .background(
                            brush = Brush.linearGradient(
                                colorStops = arrayOf(
                                    0.0f to ComposeColor(0xFFA962FF),
                                    0.85f to ComposeColor(0xFF5DEECB)
                                ),
                                start = androidx.compose.ui.geometry.Offset(
                                    0f,
                                    Float.POSITIVE_INFINITY
                                ),
                                end = androidx.compose.ui.geometry.Offset(
                                    Float.POSITIVE_INFINITY,
                                    0f
                                )
                            ),
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "말하기 어려운 순간에도\n내 목소리는 그대로.",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = ComposeColor.White,
                        textAlign = TextAlign.Center,
                        lineHeight = 38.sp
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                // CTA 버튼
                Button(
                    onClick = {
                        trainingStep = 0
                        isTrainingStarted = true
                    },
                    shape = RoundedCornerShape(40.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ComposeColor(0xFF4A7BF7)
                    ),
                    modifier = Modifier.height(48.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = if (voiceIdState != null) "내 목소리 다시 학습" else "내 목소리 학습 시작",
                            color = ComposeColor.White,
                            fontWeight = FontWeight.Medium,
                            fontSize = 17.sp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            imageVector = Icons.Default.ArrowForward,
                            contentDescription = null,
                            tint = ComposeColor.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))
            } else {
                // ── 학습 시작 화면 (피그마 디자인) ──
                if (trainingStep == 0) {
                    // 안내 문장 페이지에서 '다음' 버튼 노출
                    Box(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Column(
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = 100.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            // 흰색 원형 아이콘 (반투명 배경 + 테두리)
                            Box(
                                modifier = Modifier
                                    .size(72.dp)
                                    .graphicsLayer {
                                        val scale = if (isTrainingStarted) pulseScale else 1f
                                        scaleX = scale
                                        scaleY = scale
                                    }
                                    .background(
                                        color = ComposeColor.White.copy(alpha = pulseAlpha),
                                        shape = CircleShape
                                    )
                                    .border(
                                        width = 1.5.dp,
                                        color = ComposeColor.White.copy(alpha = pulseAlpha + 0.18f),
                                        shape = CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .background(
                                            color = ComposeColor.White,
                                            shape = CircleShape
                                        )
                                )
                            }

                            Spacer(modifier = Modifier.height(32.dp))

                            Column(
                                modifier = Modifier.height(136.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                // 메인 타이틀
                                Text(
                                    text = "안내 문장을\n편하게 읽어주세요",
                                    fontSize = 25.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = ComposeColor.White,
                                    textAlign = TextAlign.Center,
                                    lineHeight = 32.sp
                                )

                                Spacer(modifier = Modifier.height(16.dp))

                                // 부제
                                Text(
                                    text = "내 목소리를 학습하고 있어요\n평소 말하듯 편하게 읽어주세요",
                                    fontSize = 15.sp,
                                    color = ComposeColor.White.copy(alpha = 0.7f),
                                    textAlign = TextAlign.Center,
                                    lineHeight = 20.sp,
                                    minLines = 2
                                )
                            }
                        }
                        // 안내문장 하단에 '다음' 버튼 노출
                        Column(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 62.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Button(
                                onClick = { trainingStep = 1 },
                                shape = RoundedCornerShape(40.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = ComposeColor(0xFF4A7BF7)
                                ),
                                modifier = Modifier.height(48.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "다음 →",
                                        color = ComposeColor.White,
                                        fontWeight = FontWeight.Medium,
                                        fontSize = 17.sp
                                    )
                                }
                            }
                        }
                    }
                } else if (trainingStep == 4) {
                    // 4단계 진입 시 MediaExtractor+MediaMuxer로 m4a 파일 합치고 자동 재생 및 모델 학습
                    LaunchedEffect(trainingStep) {
                        if (!isMerged && audioFiles.all { it.exists() }) {
                            withContext(Dispatchers.IO) {
                                try {
                                    AudioMergeUtil.mergeM4aFiles(audioFiles, mergedFile)
                                    withContext(Dispatchers.Main) {
                                        audioPlayer?.release()
                                        audioPlayer = MediaPlayer().apply {
                                            setDataSource(mergedFile.absolutePath)
                                            prepare()
                                            start()
                                            setOnCompletionListener { isPlaying = false }
                                        }
                                        isPlaying = true
                                        isMerged = true
                                    }
                                } catch (e: Exception) {
                                    // 실패 처리(필요시)
                                }
                            }
                        }
                        // 병합 완료 후 모델 학습 백그라운드 처리
                        if (isMerged && mergedFile.exists()) {
                            coroutineScope.launch {
                                val duration = try { // m4a duration(ms) 구하기
                                    android.media.MediaMetadataRetriever().apply { setDataSource(mergedFile.absolutePath) }.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLong() ?: 20000L
                                } catch (_: Exception) { 20000L }
                                val uuid = java.util.UUID.randomUUID().toString()
                                // 기존 voiceId가 있으면 삭제 API 호출
                                val currentVoiceId = voiceIdState as? String
                                if (currentVoiceId != null) {
                                    withContext(Dispatchers.IO) {
                                        org.duckdns.dorandoran.callaiassistant.voiceclone.VoiceCloneApi.deleteVoiceClone(currentVoiceId)
                                    }
                                    org.duckdns.dorandoran.callaiassistant.voiceclone.VoiceCloneStore.setVoiceId(context, "")
                                    voiceIdFlow.value = ""
                                }
                                val trainResp = withContext(Dispatchers.IO) {
                                    org.duckdns.dorandoran.callaiassistant.voiceclone.VoiceCloneApi.trainVoiceClone(uuid, mergedFile)
                                }
                                val voiceId = trainResp?.optString("voiceId")
                                if (voiceId != null) {
                                    org.duckdns.dorandoran.callaiassistant.voiceclone.VoiceCloneStore.setVoiceId(context, voiceId)
                                    voiceIdFlow.value = voiceId
                                    org.duckdns.dorandoran.callaiassistant.voiceclone.VoiceCloneNotification.showModelComplete(context)
                                }
                            }
                        }
                    }
                    Box(modifier = Modifier.fillMaxSize()) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = 100.dp)
                                .size(290.dp)
                                .background(
                                    brush = Brush.linearGradient(
                                        colorStops = arrayOf(
                                            0.0f to ComposeColor(0xFFA962FF),
                                            0.85f to ComposeColor(0xFF5DEECB)
                                        ),
                                        start = androidx.compose.ui.geometry.Offset(
                                            0f,
                                            Float.POSITIVE_INFINITY
                                        ),
                                        end = androidx.compose.ui.geometry.Offset(
                                            Float.POSITIVE_INFINITY,
                                            0f
                                        )
                                    ),
                                    shape = CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "목소리 등록이\n완료됐어요.",
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Bold,
                                color = ComposeColor.White,
                                textAlign = TextAlign.Center,
                                lineHeight = 38.sp
                            )
                        }

                        Text(
                            text = "설정 화면에서 언제든\n다시 녹음하거나 끌 수 있어요.",
                            fontSize = 15.sp,
                            color = ComposeColor.Black,
                            textAlign = TextAlign.Center,
                            lineHeight = 22.sp,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 160.dp)
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Column(
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = 100.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            // 흰색 원형 아이콘 (반투명 배경 + 테두리)
                            Box(
                                modifier = Modifier
                                    .size(72.dp)
                                    .graphicsLayer {
                                        val scale = if (isTrainingStarted) pulseScale else 1f
                                        scaleX = scale
                                        scaleY = scale
                                    }
                                    .background(
                                        color = ComposeColor.White.copy(alpha = pulseAlpha),
                                        shape = CircleShape
                                    )
                                    .border(
                                        width = 1.5.dp,
                                        color = ComposeColor.White.copy(alpha = pulseAlpha + 0.18f),
                                        shape = CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .background(
                                            color = ComposeColor.White,
                                            shape = CircleShape
                                        )
                                )
                            }

                            Spacer(modifier = Modifier.height(32.dp))

                            Column(
                                modifier = Modifier.height(136.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                // 메인 타이틀
                                Text(
                                    text = when (trainingStep) {
                                        0 -> "안내 문장을\n편하게 읽어주세요"
                                        1 -> "“오늘 날씨는 맑고\n기분이 좋습니다.”"
                                        2 -> "“서울 지하철은\n노선이 매우 복잡합니다.”"
                                        else -> "“빨간 사과와\n파란 포도를 함께 샀습니다.”"
                                    },
                                    fontSize = 25.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = ComposeColor.White,
                                    textAlign = TextAlign.Center,
                                    lineHeight = 32.sp
                                )

                                Spacer(modifier = Modifier.height(16.dp))

                                // 부제
                                Text(
                                    text = when (trainingStep) {
                                        0 -> "내 목소리를 학습하고 있어요\n평소 말하듯 편하게 읽어주세요"
                                        1 -> "위 문장을 평소 말하듯\n편하게 읽어주세요"
                                        else -> "실수해도 다시 녹음할 수 있어요."
                                    },
                                    fontSize = 15.sp,
                                    color = ComposeColor.White.copy(alpha = 0.7f),
                                    textAlign = TextAlign.Center,
                                    lineHeight = 20.sp,
                                    minLines = 2
                                )
                            }
                        }
                        // 1~3페이지: 녹음 상태에 따라 버튼 UI/동작 분기
                        if (trainingStep in 1..3) {
                            Column(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(bottom = 62.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                if (!isRecording && recordedFilePath == null) {
                                    // 녹음 전: '음성 녹음' 원형+마이크 아이콘(파란 배경)
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Text(
                                        text = "음성 녹음",
                                        fontSize = 16.sp,
                                        color = ComposeColor.White.copy(alpha = 0.7f),
                                        textAlign = TextAlign.Center
                                    )
                                    Spacer(modifier = Modifier.height(10.dp))
                                    IconButton(
                                        onClick = {
                                            // 녹음 시작
                                            val file = File(context.cacheDir, "voice_step${trainingStep}.m4a")
                                            val rec = org.duckdns.dorandoran.callaiassistant.voiceclone.VoiceRecorder(file)
                                            rec.start()
                                            recorder.value = rec
                                            isRecording = true
                                            recordedFilePath = null
                                        },
                                        modifier = Modifier
                                            .size(52.dp)
                                            .background(
                                                color = ComposeColor(0xFF2979FF), // 파란색
                                                shape = CircleShape
                                            )
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Icon(
                                                imageVector = Icons.Default.Mic,
                                                contentDescription = "음성 녹음",
                                                tint = ComposeColor.White,
                                                modifier = Modifier.size(26.dp)
                                            )
                                        }
                                    }
                                } else if (isRecording) {
                                    // 녹음 중: '녹음 완료' 원형+중지 아이콘(빨간 배경)
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Text(
                                        text = "녹음 완료",
                                        fontSize = 16.sp,
                                        color = ComposeColor.White.copy(alpha = 0.7f),
                                        textAlign = TextAlign.Center
                                    )
                                    Spacer(modifier = Modifier.height(10.dp))
                                    IconButton(
                                        onClick = {
                                            // 녹음 완료
                                            recorder.value?.stop()
                                            recordedFilePath = File(context.cacheDir, "voice_step${trainingStep}.m4a").path
                                            isRecording = false
                                        },
                                        modifier = Modifier
                                            .size(52.dp)
                                            .background(
                                                color = ComposeColor(0xFFD32F2F), // 빨간색
                                                shape = CircleShape
                                            )
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Icon(
                                                imageVector = Icons.Default.Stop,
                                                contentDescription = "녹음 완료",
                                                tint = ComposeColor.White,
                                                modifier = Modifier.size(26.dp)
                                            )
                                        }
                                    }
                                } else if (!isRecording && recordedFilePath != null) {
                                    // 녹음 완료 후: '다음', '다시 말하기' 버튼 표시
                                    Button(
                                        onClick = { trainingStep += 1; recordedFilePath = null },
                                        shape = RoundedCornerShape(40.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = ComposeColor(0xFF4A7BF7)
                                        ),
                                        modifier = Modifier.height(48.dp)
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = "다음 →",
                                                color = ComposeColor.White,
                                                fontWeight = FontWeight.Medium,
                                                fontSize = 17.sp
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Text(
                                        text = "다시 말하기",
                                        fontSize = 16.sp,
                                        color = ComposeColor.White.copy(alpha = 0.7f),
                                        textAlign = TextAlign.Center
                                    )
                                    Spacer(modifier = Modifier.height(10.dp))
                                    IconButton(
                                        onClick = {
                                            // 기존 파일 삭제 및 녹음 재시작
                                            recordedFilePath?.let { File(it).delete() }
                                            val file = File(context.cacheDir, "voice_step${trainingStep}.m4a")
                                            val rec = org.duckdns.dorandoran.callaiassistant.voiceclone.VoiceRecorder(file)
                                            rec.start()
                                            recorder.value = rec
                                            isRecording = true
                                            recordedFilePath = null
                                        },
                                        modifier = Modifier
                                            .size(52.dp)
                                            .background(
                                                color = ComposeColor.White.copy(alpha = 0.85f),
                                                shape = CircleShape
                                            )
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Replay,
                                            contentDescription = "다시 말하기",
                                            tint = ComposeColor(0xFF4E4E4E),
                                            modifier = Modifier.size(26.dp)
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
}