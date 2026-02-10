package org.duckdns.dorandoran.callaiassistant.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.platform.LocalConfiguration
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
import androidx.compose.ui.tooling.preview.Preview
import org.duckdns.dorandoran.callaiassistant.ui.theme.CallaiassistantTheme

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

    DialerContent(
        phoneNumber = phoneNumber,
        onPhoneNumberChange = { phoneNumber = it },
        lastCalledNumber = lastCalledNumber,
        showLastCalledNumber = showLastCalledNumber,
        onShowLastCalledNumberChange = { showLastCalledNumber = it },
        contactMatches = contactMatches,
        contactTotalCount = contactTotalCount,
        onCallStarted = onCallStarted,
        onOpenSettings = onOpenSettings,
        onOpenContactSearch = onOpenContactSearch,
        onPlayTone = { digit ->
            digitToTone(digit)?.let { tone ->
                try {
                    toneGenerator.startTone(tone, 120)
                } catch (_: Throwable) {
                }
            }
        },
        modifier = modifier
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DialerContent(
    phoneNumber: String,
    onPhoneNumberChange: (String) -> Unit,
    lastCalledNumber: String?,
    showLastCalledNumber: Boolean,
    onShowLastCalledNumberChange: (Boolean) -> Unit,
    contactMatches: List<ContactMatch>,
    contactTotalCount: Int,
    onCallStarted: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenContactSearch: (String) -> Unit,
    onPlayTone: (Char) -> Unit,
    modifier: Modifier = Modifier
) {
    val configuration = LocalConfiguration.current
    val screenHeightDp = configuration.screenHeightDp

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
    ) {
        val compact = screenHeightDp <= 640
        val veryCompact = screenHeightDp <= 560

        val outerPadding = when {
            veryCompact -> 6.dp
            compact -> 12.dp
            else -> 16.dp
        }
        val sectionSpacing = when {
            veryCompact -> 4.dp
            compact -> 6.dp
            else -> 8.dp
        }
        val dialPadSpacing = when {
            veryCompact -> 2.dp
            compact -> 6.dp
            else -> 8.dp
        }
        val titleBarHeight = when {
            veryCompact -> 44.dp
            compact -> 52.dp
            else -> 56.dp
        }
        val numberAreaMinHeight = when {
            veryCompact -> 56.dp
            compact -> 72.dp
            else -> 96.dp
        }
        val contactSectionHeight = when {
            veryCompact -> 0.dp
            compact -> 64.dp
            else -> 96.dp
        }
        val actionButtonSize = when {
            veryCompact -> 56.dp
            compact -> 64.dp
            else -> 72.dp
        }
        val actionIconSize = actionButtonSize * 0.44f
        val actionRowHeight = actionButtonSize + if (veryCompact) 4.dp else 8.dp
        val numberFontSize = when {
            veryCompact -> 26.sp
            compact -> 30.sp
            else -> 34.sp
        }
        val dialKeyMinHeight = when {
            veryCompact -> 32.dp
            compact -> 42.dp
            else -> 52.dp
        }
        val dialKeyMaxHeight = when {
            veryCompact -> 48.dp
            compact -> 64.dp
            else -> 90.dp
        }

        val fixedHeights = titleBarHeight + numberAreaMinHeight + contactSectionHeight + actionRowHeight
        val fixedSpacing = sectionSpacing * 3
        val availableForDialPad = (maxHeight - fixedHeights - fixedSpacing - outerPadding * 2)
            .coerceAtLeast(0.dp)
        val availableForKeys = (availableForDialPad - dialPadSpacing * 3).coerceAtLeast(0.dp)
        val rawRowHeight = availableForKeys / 4
        var keyRowHeight = rawRowHeight.coerceIn(dialKeyMinHeight, dialKeyMaxHeight)
        if (keyRowHeight * 4 + dialPadSpacing * 3 > availableForDialPad) {
            keyRowHeight = rawRowHeight.coerceAtLeast(20.dp).coerceAtMost(dialKeyMaxHeight)
        }
        val dialPadHeight = (keyRowHeight * 4 + dialPadSpacing * 3).coerceAtMost(availableForDialPad)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(outerPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(titleBarHeight)
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

            Spacer(modifier = Modifier.height(sectionSpacing))

            // 전화번호 표시 영역
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = numberAreaMinHeight),
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
                    style = MaterialTheme.typography.headlineMedium.copy(fontSize = numberFontSize),
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                if (!showLastCalledNumber && lastCalledNumber != null && phoneNumber.isBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "마지막 통화: ${formatPhoneNumber(lastCalledNumber)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (!veryCompact) {
                Spacer(modifier = Modifier.height(sectionSpacing))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(contactSectionHeight)
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
                        Spacer(modifier = Modifier.height(contactSectionHeight))
                    }
                }
            }

            Spacer(modifier = Modifier.height(sectionSpacing))

            DialPadFixedHeight(
                phoneNumber = phoneNumber,
                onPhoneNumberChange = onPhoneNumberChange,
                onPlayTone = onPlayTone,
                keyRowHeight = keyRowHeight,
                dialPadHeight = dialPadHeight,
                dialPadSpacing = dialPadSpacing,
                compact = compact,
                veryCompact = veryCompact
            )

            Spacer(modifier = Modifier.height(sectionSpacing))

            ActionRowFixedHeight(
                height = actionRowHeight,
                buttonSize = actionButtonSize,
                iconSize = actionIconSize,
                phoneNumber = phoneNumber,
                lastCalledNumber = lastCalledNumber,
                showLastCalledNumber = showLastCalledNumber,
                onShowLastCalledNumberChange = onShowLastCalledNumberChange,
                onPhoneNumberChange = onPhoneNumberChange,
                onCallStarted = onCallStarted
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DialPadFixedHeight(
    phoneNumber: String,
    onPhoneNumberChange: (String) -> Unit,
    onPlayTone: (Char) -> Unit,
    keyRowHeight: androidx.compose.ui.unit.Dp,
    dialPadHeight: androidx.compose.ui.unit.Dp,
    dialPadSpacing: androidx.compose.ui.unit.Dp,
    compact: Boolean,
    veryCompact: Boolean
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(dialPadHeight),
        verticalArrangement = Arrangement.spacedBy(dialPadSpacing)
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
                modifier = Modifier
                    .fillMaxWidth()
                    .height(keyRowHeight),
                horizontalArrangement = Arrangement.spacedBy(dialPadSpacing)
            ) {
                row.forEach { key ->
                    DialPadButtonResponsive(
                        digit = key.digit,
                        hangul = key.hangul,
                        latin = key.latin,
                        modifier = Modifier.weight(1f),
                        compact = compact,
                        veryCompact = veryCompact,
                        keyRowHeight = keyRowHeight,
                        onClick = {
                            if (key.digit.length == 1) {
                                onPlayTone(key.digit[0])
                            }
                            onPhoneNumberChange(phoneNumber + key.digit)
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ActionRowFixedHeight(
    height: androidx.compose.ui.unit.Dp,
    buttonSize: androidx.compose.ui.unit.Dp,
    iconSize: androidx.compose.ui.unit.Dp,
    phoneNumber: String,
    lastCalledNumber: String?,
    showLastCalledNumber: Boolean,
    onShowLastCalledNumberChange: (Boolean) -> Unit,
    onPhoneNumberChange: (String) -> Unit,
    onCallStarted: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(height),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Spacer(modifier = Modifier.size(buttonSize))
        CallButtonSized(
            size = buttonSize,
            iconSize = iconSize,
            enabled = true,
            onClick = {
                when {
                    phoneNumber.isBlank() && lastCalledNumber != null && !showLastCalledNumber -> {
                        onShowLastCalledNumberChange(true)
                    }
                    showLastCalledNumber && lastCalledNumber != null -> {
                        onCallStarted(lastCalledNumber)
                        onShowLastCalledNumberChange(false)
                    }
                    phoneNumber.isNotBlank() -> {
                        onCallStarted(phoneNumber)
                    }
                }
            }
        )
        Box(
            modifier = Modifier
                .size(buttonSize)
                .combinedClickable(
                    onClick = {
                        if (showLastCalledNumber) {
                            onShowLastCalledNumberChange(false)
                        } else if (phoneNumber.isNotEmpty()) {
                            onPhoneNumberChange(phoneNumber.dropLast(1))
                        }
                    },
                    onLongClick = {
                        onPhoneNumberChange("")
                        onShowLastCalledNumberChange(false)
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

@Composable
private fun DialPadButtonResponsive(
    digit: String,
    hangul: String,
    latin: String,
    modifier: Modifier = Modifier,
    compact: Boolean,
    veryCompact: Boolean,
    keyRowHeight: androidx.compose.ui.unit.Dp,
    onClick: () -> Unit
) {
    val base = keyRowHeight.value
    val digitSize = (base * 0.55f).coerceIn(14f, 24f).sp
    val hangulSize = (base * 0.22f).coerceIn(6f, 12f).sp
    val latinSize = (base * 0.2f).coerceIn(6f, 11f).sp
    val labelSpacing = if (veryCompact) 4.dp else 6.dp
    Box(
        modifier = modifier
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (compact) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = digit,
                    fontSize = digitSize,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.width(labelSpacing))
                Column(
                    horizontalAlignment = Alignment.Start,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = hangul,
                        fontSize = hangulSize,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = if (latin.isBlank()) " " else latin,
                        fontSize = latinSize,
                        color = if (latin.isBlank()) Color.Transparent else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = digit,
                    fontSize = digitSize,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = hangul,
                    fontSize = hangulSize,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(1.dp))
                Text(
                    text = if (latin.isBlank()) " " else latin,
                    fontSize = latinSize,
                    color = if (latin.isBlank()) Color.Transparent else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private data class DialPadKey(
    val digit: String,
    val hangul: String,
    val latin: String
)

@Composable
private fun CallButtonSized(
    size: androidx.compose.ui.unit.Dp,
    iconSize: androidx.compose.ui.unit.Dp,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(size)
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
            modifier = Modifier.size(iconSize)
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

@Preview(showBackground = true)
@Composable
fun DialerScreenPreview() {
    CallaiassistantTheme {
        DialerContent(
            phoneNumber = "0101234",
            onPhoneNumberChange = {},
            lastCalledNumber = "01098765432",
            showLastCalledNumber = false,
            onShowLastCalledNumberChange = {},
            contactMatches = listOf(
                ContactMatch("홍길동", "01012345678"),
                ContactMatch("김철수", "01012344321")
            ),
            contactTotalCount = 5,
            onCallStarted = {},
            onOpenSettings = {},
            onOpenContactSearch = {},
            onPlayTone = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
fun DialerScreenEmptyPreview() {
    CallaiassistantTheme {
        DialerContent(
            phoneNumber = "",
            onPhoneNumberChange = {},
            lastCalledNumber = null,
            showLastCalledNumber = false,
            onShowLastCalledNumberChange = {},
            contactMatches = emptyList(),
            contactTotalCount = 0,
            onCallStarted = {},
            onOpenSettings = {},
            onOpenContactSearch = {},
            onPlayTone = {}
        )
    }
}
