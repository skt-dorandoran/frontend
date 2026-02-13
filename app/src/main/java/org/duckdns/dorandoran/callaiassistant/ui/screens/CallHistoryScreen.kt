package org.duckdns.dorandoran.callaiassistant.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.duckdns.dorandoran.callaiassistant.R
import org.duckdns.dorandoran.callaiassistant.data.CallLogItem
import org.duckdns.dorandoran.callaiassistant.data.CallLogRepository
import org.duckdns.dorandoran.callaiassistant.data.CallType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun CallHistoryScreen(
    onCallNumber: (String) -> Unit = {},
    modifier: Modifier = Modifier,
    onOpenDialer: () -> Unit = {}
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val repository = remember { CallLogRepository(context) }
    var callHistory by remember { mutableStateOf<List<CallLogItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    val pretendardFont = try {
        FontFamily(Font(R.font.pretendard_medium, FontWeight.Medium))
    } catch (_: Exception) {
        FontFamily.Default
    }

    val pretendardBoldFont = try {
        FontFamily(Font(R.font.pretendard_bold, FontWeight.Bold))
    } catch (_: Exception) {
        FontFamily.Default
    }

    LaunchedEffect(Unit) {
        callHistory = repository.getCallHistory()
        isLoading = false
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(top = 16.dp, bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "전화 기록",
            style = TextStyle(
                fontSize = 20.sp,
                fontFamily = pretendardBoldFont,
                fontWeight = FontWeight(700)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 32.dp, top = 16.dp, end = 16.dp, bottom = 16.dp)
        )

        // 리스트 영역 (남은 공간 차지)
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            if (isLoading) {
                CircularProgressIndicator()
            } else if (callHistory.isEmpty()) {
                Text(
                    text = "전화 기록이 없습니다",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(callHistory) { call ->
                        CallHistoryItem(
                            call = call,
                            onClick = { onCallNumber(call.phoneNumber) },
                            pretendardFont = pretendardFont
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 하단 토글 바
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
                // 최근 기록 (Active)
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
                        text = "최근 기록",
                        style = TextStyle(
                            fontSize = 15.sp,
                            fontFamily = pretendardFont,
                            fontWeight = FontWeight(700),
                            color = Color(0xFF1D1D1F),
                            textAlign = TextAlign.Center,
                        )
                    )
                }

                // 키패드 (Inactive)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(39.dp)
                        .clip(RoundedCornerShape(23.dp))
                        .clickable { onOpenDialer() },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "키패드",
                        style = TextStyle(
                            fontSize = 15.sp,
                            fontFamily = pretendardFont,
                            fontWeight = FontWeight(500),
                            color = Color(0xFF73777F),
                            textAlign = TextAlign.Center,
                        )
                    )
                }
            }
        }
    }
}

@Composable
fun CallHistoryItem(
    call: CallLogItem,
    onClick: () -> Unit,
    pretendardFont: FontFamily = FontFamily.Default
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Call,
                contentDescription = null,
                tint = when (call.callType) {
                    CallType.INCOMING -> MaterialTheme.colorScheme.primary
                    CallType.OUTGOING -> MaterialTheme.colorScheme.tertiary
                    CallType.MISSED -> MaterialTheme.colorScheme.error
                },
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.size(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = call.displayName,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontFamily = pretendardFont
                    )
                )
                if (call.contactName != null && call.phoneNumber.isNotBlank()) {
                    Text(
                        text = call.phoneNumber,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = pretendardFont
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = formatCallDate(call.date) + " • " + formatHistoryDuration(call.duration) + " • " + formatHistoryCallType(call.callType),
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = pretendardFont
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = Icons.Default.Call,
                contentDescription = "통화",
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

fun formatCallDate(date: Date): String {
    val now = Date()
    val diff = now.time - date.time
    return when {
        diff < 24 * 60 * 60 * 1000 -> SimpleDateFormat("HH:mm", Locale.getDefault()).format(date)
        diff < 7 * 24 * 60 * 60 * 1000 -> SimpleDateFormat("EEE HH:mm", Locale.getDefault()).format(date)
        else -> SimpleDateFormat("MM/dd HH:mm", Locale.getDefault()).format(date)
    }
}

fun formatHistoryDuration(seconds: Long): String {
    return when {
        seconds < 60 -> "${seconds}초"
        seconds < 3600 -> "${seconds / 60}분 ${seconds % 60}초"
        else -> "${seconds / 3600}시간 ${(seconds % 3600) / 60}분"
    }
}

fun formatHistoryCallType(type: CallType): String = when (type) {
    CallType.INCOMING -> "수신"
    CallType.OUTGOING -> "발신"
    CallType.MISSED -> "부재중"
}
