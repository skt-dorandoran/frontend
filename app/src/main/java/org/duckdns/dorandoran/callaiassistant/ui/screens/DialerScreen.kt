package org.duckdns.dorandoran.callaiassistant.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.media.AudioManager
import android.media.ToneGenerator
import org.duckdns.dorandoran.callaiassistant.R
import org.duckdns.dorandoran.callaiassistant.data.CallLogRepository
import org.duckdns.dorandoran.callaiassistant.data.CallLogRepository.ContactMatch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DialerScreen(
    modifier: Modifier = Modifier,
    initialPhoneNumber: String = "",
    lastCalledNumber: String? = null,
    onCallStarted: (String) -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onOpenContactSearch: (String) -> Unit = {},
    onOpenCallHistory: () -> Unit = {},
) {
    var phoneNumber by remember(initialPhoneNumber) { mutableStateOf(initialPhoneNumber) }
    var showLastCalledNumber by remember { mutableStateOf(false) }
    val context = LocalContext.current

    val toneGenerator = remember { ToneGenerator(AudioManager.STREAM_DTMF, 80) }
    val repository = remember { CallLogRepository(context) }
    var contactMatches by remember { mutableStateOf<List<ContactMatch>>(emptyList()) }
    var contactTotalCount by remember { mutableIntStateOf(0) }

    val pretendardFont = try {
        FontFamily(Font(R.font.pretendard_medium, FontWeight.Medium))
    } catch (_: Exception) {
        FontFamily.Default
    }

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
            .padding(top = 16.dp, bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // 1. 헤더
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .height(43.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "T.mate",
                style = TextStyle(
                    fontSize = 35.sp,
                    fontFamily = pretendardFont,
                    fontWeight = FontWeight(700),
                    color = Color(0xFF1D1D1F)
                )
            )
            IconButton(
                onClick = onOpenSettings,
                modifier = Modifier
                    .size(34.dp)
                    .padding(1.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "설정",
                    tint = Color(0xFF1D1D1F),
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        // 2. 전화번호 표시
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
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontFamily = pretendardFont,
                    fontWeight = FontWeight.Medium
                ),
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

        // 3. 연락처 검색 결과
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            if (contactMatches.isNotEmpty()) {
                val match = contactMatches.first()
                TextButton(onClick = { onCallStarted(match.number) }) {
                    Text(
                        text = "${match.name} ",
                        style = TextStyle(
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1D1D1F)
                        )
                    )
                    Text(
                        text = buildHighlightedNumber(
                            formatPhoneNumber(match.number),
                            phoneNumber.filter { it.isDigit() }
                        ),
                        style = TextStyle(
                            fontSize = 14.sp,
                            color = Color.Gray
                        )
                    )
                }
            } else if (contactTotalCount > 0) {
                TextButton(onClick = { onOpenContactSearch(phoneNumber) }) {
                    Text("검색 결과 ${contactTotalCount}건 보기 >")
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // 4. 키패드
        Column(
            modifier = Modifier
                .width(271.dp)
                .height(305.dp),
            verticalArrangement = Arrangement.spacedBy(19.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val dialPad = listOf(
                listOf(DialPadKey("1", "ㄱㅋ", ".QZ"), DialPadKey("2", "ㄴ", "ABC"), DialPadKey("3", "ㄷㅌ", "DEF")),
                listOf(DialPadKey("4", "ㄹ", "GHI"), DialPadKey("5", "ㅁ", "JKL"), DialPadKey("6", "ㅂㅍ", "NMO")),
                listOf(DialPadKey("7", "ㅅ", "PRS"), DialPadKey("8", "ㅇ", "TUV"), DialPadKey("9", "ㅈㅊ", "WXY")),
                listOf(DialPadKey("*", ",", ""), DialPadKey("0", "ㅎ", "+"), DialPadKey("#", ";", ""))
            )

            dialPad.forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    row.forEach { key ->
                        DialPadButton(
                            digit = key.digit,
                            hangul = key.hangul,
                            latin = key.latin,
                            modifier = Modifier
                                .weight(1f)
                                .height(66.dp),
                            onClick = {
                                if (key.digit.length == 1) {
                                    digitToTone(key.digit[0])?.let { tone ->
                                        try { toneGenerator.startTone(tone, 120) } catch (_: Throwable) {}
                                    }
                                }
                                phoneNumber += key.digit
                            }
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // 5. 하단 영역 (통화 버튼 + 토글 바)
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // (1) 통화 버튼 Row
            Row(
                modifier = Modifier.width(271.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Spacer(modifier = Modifier.size(72.dp))

                CallButton(
                    onClick = {
                        when {
                            phoneNumber.isBlank() && lastCalledNumber != null && !showLastCalledNumber -> {
                                showLastCalledNumber = true
                            }
                            showLastCalledNumber && lastCalledNumber != null -> {
                                onCallStarted(lastCalledNumber)
                                showLastCalledNumber = false
                            }
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
                                if (showLastCalledNumber) {
                                    showLastCalledNumber = false
                                } else if (phoneNumber.isNotEmpty()) {
                                    phoneNumber = phoneNumber.dropLast(1)
                                }
                            },
                            onLongClick = {
                                phoneNumber = ""
                                showLastCalledNumber = false
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Backspace,
                        contentDescription = "삭제",
                        tint = Color(0xFF000000)
                    )
                }
            }

            // (2) 하단 토글 바
            Box(
                modifier = Modifier
                    .width(363.dp)
                    .height(47.dp)
                    .background(color = Color(0xFFF4F5F8), shape = RoundedCornerShape(size = 23.dp)),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .width(167.dp)
                            .height(39.dp)
                            .clip(RoundedCornerShape(23.dp))
                            .clickable { onOpenCallHistory() }
                            .padding(10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "최근 기록",
                            style = TextStyle(
                                fontSize = 15.sp,
                                fontFamily = pretendardFont,
                                fontWeight = FontWeight(500),
                                color = Color(0xFF73777F),
                                textAlign = TextAlign.Center,
                            )
                        )
                    }

                    Box(
                        modifier = Modifier
                            .shadow(
                                elevation = 24.dp,
                                spotColor = Color(0x33959DA5),
                                ambientColor = Color(0x33959DA5),
                                shape = RoundedCornerShape(size = 23.dp)
                            )
                            .width(167.dp)
                            .height(39.dp)
                            .background(color = Color(0xFFFFFFFF), shape = RoundedCornerShape(size = 23.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "키패드",
                            style = TextStyle(
                                fontSize = 15.sp,
                                fontFamily = pretendardFont,
                                fontWeight = FontWeight(500),
                                color = Color(0xFF1D1D1F),
                                textAlign = TextAlign.Center,
                            )
                        )
                    }
                }
            }
        }
    }
}

// ▼▼▼ 하위 컴포넌트 및 유틸리티 함수들 ▼▼▼

@Composable
private fun DialPadButton(
    digit: String,
    hangul: String,
    latin: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val pretendard = try {
        FontFamily(Font(R.font.pretendard_medium, FontWeight.Medium))
    } catch (_: Exception) { FontFamily.Default }

    val digitColor = Color(0xFF000000)

    Box(
        modifier = modifier.clickable(onClick = onClick),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {
            Box(
                modifier = Modifier.height(38.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = digit,
                    style = TextStyle(
                        fontSize = 32.sp,
                        fontFamily = pretendard,
                        fontWeight = FontWeight(500),
                        color = digitColor,
                        textAlign = TextAlign.Center
                    )
                )
            }
            Column(
                modifier = Modifier.height(28.dp),
                verticalArrangement = Arrangement.spacedBy(1.dp, Alignment.Top),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (hangul.isNotBlank()) {
                    Text(
                        text = hangul,
                        style = TextStyle(
                            fontSize = 12.sp,
                            fontFamily = pretendard,
                            fontWeight = FontWeight(600),
                            color = digitColor,
                            textAlign = TextAlign.Center,
                            letterSpacing = 1.2.sp
                        ),
                        modifier = Modifier.height(14.dp)
                    )
                }
                Text(
                    text = latin.ifBlank { " " },
                    style = TextStyle(
                        fontSize = 11.sp,
                        fontFamily = pretendard,
                        fontWeight = FontWeight(500),
                        color = if (latin.isBlank()) Color.Transparent else digitColor,
                        textAlign = TextAlign.Center
                    ),
                    modifier = Modifier.height(13.dp)
                )
            }
        }
    }
}

private data class DialPadKey(val digit: String, val hangul: String, val latin: String)

@Composable
private fun CallButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .shadow(
                elevation = 24.dp,
                shape = CircleShape,
                spotColor = Color(0x33959DA5),
                ambientColor = Color(0x33959DA5)
            )
            .size(73.dp)
            .background(color = Color.White, shape = CircleShape)
            .border(width = 1.dp, color = Color(0xFFEEF0F5), shape = CircleShape)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.Call,
            contentDescription = "통화",
            tint = Color(0xFF2E7D32),
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

// ★★★ 이 함수가 누락되어 에러가 났습니다. 다시 포함시켰습니다! ★★★
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