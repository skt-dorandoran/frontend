package org.duckdns.dorandoran.callaiassistant.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import org.duckdns.dorandoran.callaiassistant.data.CallLogItem
import org.duckdns.dorandoran.callaiassistant.data.CallLogRepository
import org.duckdns.dorandoran.callaiassistant.data.CallType
import org.duckdns.dorandoran.callaiassistant.PhoneCallHelper
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun CallHistoryScreen(
    onCallNumber: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val repository = remember { CallLogRepository(context) }
    var callHistory by remember { mutableStateOf<List<CallLogItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        callHistory = repository.getCallHistory()
        isLoading = false
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "전화 기록",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (callHistory.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "전화 기록이 없습니다",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(callHistory) { call ->
                    CallHistoryItem(
                        call = call,
                        onClick = {
                            onCallNumber(call.phoneNumber)
                            PhoneCallHelper.makeCall(context, call.phoneNumber)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun CallHistoryItem(
    call: CallLogItem,
    onClick: () -> Unit
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
                    style = MaterialTheme.typography.titleMedium
                )
                if (call.contactName != null && call.phoneNumber.isNotBlank()) {
                    Text(
                        text = call.phoneNumber,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = formatCallDate(call.date) + " • " + formatDuration(call.duration) + " • " + formatCallType(call.callType),
                    style = MaterialTheme.typography.bodySmall,
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

private fun formatCallDate(date: Date): String {
    val now = Date()
    val diff = now.time - date.time
    return when {
        diff < 24 * 60 * 60 * 1000 -> SimpleDateFormat("HH:mm", Locale.getDefault()).format(date)
        diff < 7 * 24 * 60 * 60 * 1000 -> SimpleDateFormat("EEE HH:mm", Locale.getDefault()).format(date)
        else -> SimpleDateFormat("MM/dd HH:mm", Locale.getDefault()).format(date)
    }
}

private fun formatDuration(seconds: Long): String {
    return when {
        seconds < 60 -> "${seconds}초"
        seconds < 3600 -> "${seconds / 60}분 ${seconds % 60}초"
        else -> "${seconds / 3600}시간 ${(seconds % 3600) / 60}분"
    }
}

private fun formatCallType(type: CallType): String = when (type) {
    CallType.INCOMING -> "수신"
    CallType.OUTGOING -> "발신"
    CallType.MISSED -> "부재중"
}
