package org.duckdns.dorandoran.callaiassistant.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
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
    val headerWidth = 276.dp
    val keypadWidth = 272.dp
    val keypadColumnShift = 13.dp

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
            .background(Color.White)
            .padding(top = 28.dp, bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 1. 헤더 (고정) - 피그마 치수인 289dp로 복구 완료
        Row(
            modifier = Modifier
                .width(headerWidth)
                .height(43.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "T.mate",
                style = TextStyle(
                    fontSize = 30.sp,
                    fontFamily = pretendardFont,
                    fontWeight = FontWeight(700),
                    color = Color(0xFF1D1D1F)
                )
            )
            // IconButton의 기본 패딩이 레이아웃을 왜곡하는 것을 막기 위해 Box로 교체
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .clickable { onOpenSettings() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.setting),
                    contentDescription = "설정",
                    tint = Color(0xFF323683),
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        // 2 & 3. 번호 표시 및 검색 결과
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
                style = TextStyle(
                    fontSize = 28.sp,
                    lineHeight = 22.sp,
                    fontFamily = pretendardFont,
                    fontWeight = FontWeight(900),
                    color = Color(0xFF000000),
                    textAlign = TextAlign.Center,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 44.dp)
                    .wrapContentHeight(Alignment.CenterVertically)
            )

            if (!showLastCalledNumber && lastCalledNumber != null && phoneNumber.isBlank()) {
                Text(
                    text = "마지막 통화: ${formatPhoneNumber(lastCalledNumber)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp),
                contentAlignment = Alignment.Center
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
        }

        Spacer(modifier = Modifier.height(24.dp))

        // 4. 키패드
        Column(
            modifier = Modifier
                .width(keypadWidth)
                .offset(y = 2.dp)
                .wrapContentHeight(),
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
                    row.forEachIndexed { index, key ->
                        val horizontalShift = when (index) {
                            0 -> -keypadColumnShift
                            2 -> keypadColumnShift
                            else -> 0.dp
                        }

                        DialPadButton(
                            digit = key.digit,
                            hangul = key.hangul,
                            latin = key.latin,
                            modifier = Modifier
                                .weight(1f)
                                .height(66.dp)
                                .offset(x = horizontalShift),
                            onClick = {
                                if (key.digit.length == 1) {
                                    digitToTone(key.digit[0])?.let { tone ->
                                        try { toneGenerator.startTone(tone, 120) } catch (_: Throwable) {}
                                    }
                                }
                                if (phoneNumber.length < 11) {
                                    phoneNumber += key.digit
                                }
                            }
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // 5. 하단 영역 (통화 버튼 + 토글 바)
        Column(
            modifier = Modifier.offset(y = 2.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
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

                if (phoneNumber.isNotEmpty() || showLastCalledNumber) {
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
                            painter = painterResource(id = R.drawable.backspace),
                            contentDescription = "삭제",
                            tint = Color.Unspecified
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.size(72.dp))
                }
            }

            // (2) 하단 토글 바
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(47.dp)
                        .background(color = Color(0xFFF4F5F8), shape = RoundedCornerShape(size = 23.dp))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 최근 기록 (Inactive)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(39.dp)
                            .clip(RoundedCornerShape(23.dp))
                            .clickable { onOpenCallHistory() },
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

                    // 키패드 (Active)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(39.dp)
                            .shadow(
                                elevation = 24.dp,
                                spotColor = Color(0x33959DA5),
                                ambientColor = Color(0x33959DA5),
                                shape = RoundedCornerShape(size = 23.dp)
                            )
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
                        fontWeight = FontWeight(650),
                        color = digitColor,
                        textAlign = TextAlign.Center
                    ),
                    modifier = when (digit) {
                        "*" -> Modifier.offset(y = 7.dp)
                        "0", "#" -> Modifier.offset(x = (-1).dp)
                        else -> Modifier
                    }
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
                        modifier = Modifier
                            .height(14.dp)
                            .offset(y = if (hangul == ";") 4.dp else 0.dp)
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

fun Modifier.figmaDropShadow(
    color: Color = Color(0xFF959DA5),
    alpha: Float = 0.1f, // 투명도 20%
    blurRadius: Dp = 24.dp, // 흐림 24
    offsetX: Dp = 0.dp, // X 0
    offsetY: Dp = 8.dp, // Y 8
) = this.drawBehind {
    val shadowColor = color.copy(alpha = alpha).toArgb()
    val transparentColor = color.copy(alpha = 0f).toArgb()

    drawIntoCanvas { canvas ->
        val paint = Paint()
        val frameworkPaint = paint.asFrameworkPaint()
        frameworkPaint.color = transparentColor

        frameworkPaint.setShadowLayer(
            blurRadius.toPx(),
            offsetX.toPx(),
            offsetY.toPx(),
            shadowColor
        )

        canvas.drawCircle(
            center = center,
            radius = size.width / 2,
            paint = paint
        )
    }
}

@Composable
private fun CallButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .figmaDropShadow()
            .size(73.dp)
            .background(color = Color(0xFFFFFFFF), shape = CircleShape)
            .border(width = 1.dp, color = Color(0xFFEEF0F5), shape = CircleShape)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(id = R.drawable.phone_icon),
            contentDescription = "통화",
            tint = Color(0xFF5BC774),
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
