package org.duckdns.dorandoran.callaiassistant

import android.os.Bundle
import android.content.Intent
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.duckdns.dorandoran.callaiassistant.data.CallLogRepository
import org.duckdns.dorandoran.callaiassistant.data.CallLogRepository.ContactMatch
import org.duckdns.dorandoran.callaiassistant.ui.theme.CallaiassistantTheme

class ContactSearchResultsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val query = intent?.getStringExtra(EXTRA_QUERY).orEmpty()
        setContent {
            CallaiassistantTheme {
                ContactSearchResultsContent(
                    query = query,
                    onBack = { finish() },
                    onCallNumber = { number ->
                        val digits = number.filter { it.isDigit() || it == '+' }
                        val intent = Intent(this, MainActivity::class.java).apply {
                            data = Uri.parse("tel:$digits")
                            putExtra(MainActivity.EXTRA_AUTO_CALL, true)
                        }
                        startActivity(intent)
                        finish()
                    }
                )
            }
        }
    }

    companion object {
        const val EXTRA_QUERY = "extra_query"
    }
}

@Composable
private fun ContactSearchResultsContent(
    query: String,
    onBack: () -> Unit,
    onCallNumber: (String) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val repository = remember { CallLogRepository(context) }
    var matches by remember { mutableStateOf<List<ContactMatch>>(emptyList()) }
    var totalCount by remember { mutableStateOf(0) }
    val queryDigits = query.filter { it.isDigit() }

    LaunchedEffect(query) {
        val (result, total) = repository.searchContacts(query, 200)
        matches = result
        totalCount = total
    }

    BackHandler { onBack() }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(24.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "뒤로가기"
                    )
                }
                Text(
                    text = "연락처 검색 결과",
                    style = MaterialTheme.typography.headlineSmall
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "연락처 (${totalCount})",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(8.dp))

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
            ) {
                items(matches) { contact ->
                    ContactResultItem(
                        contact = contact,
                        queryDigits = queryDigits,
                        onClick = { onCallNumber(contact.number) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ContactResultItem(
    contact: ContactMatch,
    queryDigits: String,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp)
    ) {
        Text(
            text = contact.name,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = buildHighlightedNumber(contact.number, queryDigits),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        HorizontalDivider(modifier = Modifier.padding(top = 8.dp))
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
