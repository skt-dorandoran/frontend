package org.duckdns.dorandoran.callaiassistant.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import org.duckdns.dorandoran.callaiassistant.data.CallLogRepository
import org.duckdns.dorandoran.callaiassistant.data.CallLogRepository.ContactMatch
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.graphics.Color
import android.media.AudioManager
import android.media.ToneGenerator

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DialerScreen(
    initialPhoneNumber: String = "",
    lastCalledNumber: String? = null,
    onCallStarted: (String) -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onOpenContactSearch: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var phoneNumber by remember(initialPhoneNumber) { mutableStateOf(initialPhoneNumber) }
    var showLastCalledNumber by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val audioManager = remember { context.getSystemService(android.content.Context.AUDIO_SERVICE) as AudioManager }
    val toneGenerator = remember { ToneGenerator(AudioManager.STREAM_DTMF, 80) }
    val repository = remember { CallLogRepository(context) }
    var contactMatches by remember { mutableStateOf<List<ContactMatch>>(emptyList()) }
    var contactTotalCount by remember { mutableStateOf(0) }

    DisposableEffect(Unit) {
        onDispose { toneGenerator.release() }
    }

    LaunchedEffect(phoneNumber, showLastCalledNumber) {
        if (showLastCalledNumber || phoneNumber.isBlank()) {
            contactMatches = emptyList()
            contactTotalCount = 0
        } else {
            val (matches, total) = repository.searchContacts(phoneNumber, 3)
            contactMatches = matches
            contactTotalCount = total
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
        ) {
            Text(
                text = "T.mate",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.align(Alignment.CenterStart)
            )
            IconButton(
                onClick = onOpenSettings,
                modifier = Modifier.align(Alignment.CenterEnd)
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "환경설정"
                )
            }
        }
        // 전화번호 표시 영역
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = if (showLastCalledNumber && lastCalledNumber != null) {
                    formatPhoneNumber(lastCalledNumber)
                } else if (phoneNumber.isBlank()) {
                    ""
                } else {
                    formatPhoneNumber(phoneNumber)
                },
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            if (!showLastCalledNumber && lastCalledNumber != null && phoneNumber.isBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "마지막 통화: ${formatPhoneNumber(lastCalledNumber)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
                .height(96.dp)
        ) {
            if (contactMatches.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    contactMatches.forEach { match ->
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 4.dp)
                                .clickable { onCallStarted(match.number) },
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = match.name,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = buildHighlightedNumber(
                                    formatPhoneNumber(match.number),
                                    phoneNumber.filter { it.isDigit() }
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }

                val moreCount = contactTotalCount - contactMatches.size
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    if (moreCount > 0) {
                        TextButton(onClick = { onOpenContactSearch(phoneNumber) }) {
                            Text("${moreCount}건 더보기")
                        }
                    } else {
                        Spacer(modifier = Modifier.height(36.dp))
                    }
                }
            } else {
                Spacer(modifier = Modifier.height(96.dp))
            }
        }

        // 다이얼 패드
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val dialPad = listOf(
                listOf(
                    DialPadKey("1", "ㄱㅋ", ".QZ"),
                    DialPadKey("2", "ㄴ", "ABC"),
                    DialPadKey("3", "ㄷㅌ", "DEF")
                ),
                listOf(
                    DialPadKey("4", "ㄹ", "GHI"),
                    DialPadKey("5", "ㅁ", "JKL"),
                    DialPadKey("6", "ㅂㅍ", "NMO")
                ),
                listOf(
                    DialPadKey("7", "ㅅ", "PRS"),
                    DialPadKey("8", "ㅇ", "TUV"),
                    DialPadKey("9", "ㅈㅊ", "WXY")
                ),
                listOf(
                    DialPadKey("*", ",", ""),
                    DialPadKey("0", "ㅎ", "+"),
                    DialPadKey("#", ";", "")
                )
            )

            dialPad.forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    row.forEach { key ->
                        DialPadButton(
                            digit = key.digit,
                            hangul = key.hangul,
                            latin = key.latin,
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1.4f),
                            onClick = {
                                if (key.digit.length == 1) {
                                    digitToTone(key.digit[0])?.let { tone ->
                                        try {
                                            toneGenerator.startTone(tone, 120)
                                        } catch (_: Throwable) {
                                        }
                                    }
                                }
                                phoneNumber += key.digit
                            }
                        )
                    }
                }
            }

            // 삭제 및 통화 버튼 행
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Spacer(modifier = Modifier.size(72.dp))
                CallButton(
                    enabled = true,
                    onClick = {
                        when {
                            // 번호가 비어있고 마지막 통화 번호가 있으면 표시
                            phoneNumber.isBlank() && lastCalledNumber != null && !showLastCalledNumber -> {
                                showLastCalledNumber = true
                            }
                            // 이미 마지막 번호를 표시중이면 그 번호로 통화
                            showLastCalledNumber && lastCalledNumber != null -> {
                                onCallStarted(lastCalledNumber)
                                showLastCalledNumber = false
                            }
                            // 번호가 입력되어있으면 그 번호로 통화
                            phoneNumber.isNotBlank() -> {
                                onCallStarted(phoneNumber)
                            }
                        }
                    }
                )
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .combinedClickable(
                            onClick = {
                                // 짧은 클릭: 한 글자 삭제 또는 마지막 번호 표시 취소
                                if (showLastCalledNumber) {
                                    showLastCalledNumber = false
                                } else if (phoneNumber.isNotEmpty()) {
                                    phoneNumber = phoneNumber.dropLast(1)
                                }
                            },
                            onLongClick = {
                                // 긴 클릭: 전체 삭제
                                phoneNumber = ""
                                showLastCalledNumber = false
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Backspace,
                        contentDescription = "삭제"
                    )
                }
            }
        }
    }
}

@Composable
private fun DialPadButton(
    digit: String,
    hangul: String,
    latin: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = digit,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = hangul,
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = if (latin.isBlank()) " " else latin,
                fontSize = 9.sp,
                color = if (latin.isBlank()) Color.Transparent else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private data class DialPadKey(
    val digit: String,
    val hangul: String,
    val latin: String
)

@Composable
private fun CallButton(
    enabled: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(72.dp)
            .clip(CircleShape)
            .background(
                if (enabled) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surfaceVariant
            )
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.Call,
            contentDescription = "통화",
            tint = if (enabled) MaterialTheme.colorScheme.onPrimary
            else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(32.dp)
        )
    }
}

private fun formatPhoneNumber(number: String): String {
    if (number.any { !it.isDigit() }) return number
    val digits = number.filter { it.isDigit() }
    return when {
        digits.length <= 3 -> digits
        digits.length <= 7 -> "${digits.take(3)}-${digits.drop(3)}"
        else -> "${digits.take(3)}-${digits.drop(3).take(4)}-${digits.drop(7)}"
    }
}

private fun buildHighlightedNumber(text: String, queryDigits: String): AnnotatedString {
    if (queryDigits.isBlank()) return AnnotatedString(text)
    val digitsOnly = text.filter { it.isDigit() }
    val startIndex = digitsOnly.indexOf(queryDigits)
    if (startIndex < 0) return AnnotatedString(text)
    val endIndex = startIndex + queryDigits.length

    return buildAnnotatedString {
        var digitIndex = 0
        text.forEach { ch ->
            val isDigit = ch.isDigit()
            val inMatch = isDigit && digitIndex in startIndex until endIndex
            if (inMatch) {
                pushStyle(SpanStyle(color = Color(0xFF2E7D32)))
                append(ch)
                pop()
            } else {
                append(ch)
            }
            if (isDigit) digitIndex++
        }
    }
}

private fun digitToTone(digit: Char): Int? {
    return when (digit) {
        '0' -> ToneGenerator.TONE_DTMF_0
        '1' -> ToneGenerator.TONE_DTMF_1
        '2' -> ToneGenerator.TONE_DTMF_2
        '3' -> ToneGenerator.TONE_DTMF_3
        '4' -> ToneGenerator.TONE_DTMF_4
        '5' -> ToneGenerator.TONE_DTMF_5
        '6' -> ToneGenerator.TONE_DTMF_6
        '7' -> ToneGenerator.TONE_DTMF_7
        '8' -> ToneGenerator.TONE_DTMF_8
        '9' -> ToneGenerator.TONE_DTMF_9
        '*' -> ToneGenerator.TONE_DTMF_S
        '#' -> ToneGenerator.TONE_DTMF_P
        else -> null
    }
}
