package org.duckdns.dorandoran.callaiassistant

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.widget.EditText
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import org.duckdns.dorandoran.callaiassistant.ui.theme.CallaiassistantTheme

class MyPhoneNumberActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CallaiassistantTheme {
                MyPhoneNumberContent(onBack = { finish() })
            }
        }
    }
}

@Composable
private fun MyPhoneNumberContent(onBack: () -> Unit) {
    val context = LocalContext.current
    var inputNumber by remember { mutableStateOf(SettingsStore.getMyPhoneNumber(context)) }

    LaunchedEffect(Unit) {
        val deviceNumber = getDevicePhoneNumber(context)
        inputNumber = SettingsStore.getMyPhoneNumber(context).ifBlank {
            if (deviceNumber.isNotBlank()) deviceNumber else SettingsStore.DEFAULT_MY_PHONE_NUMBER
        }
    }

    fun saveAndClose() {
        val value = inputNumber.ifBlank { SettingsStore.DEFAULT_MY_PHONE_NUMBER }
        SettingsStore.setMyPhoneNumber(context, value)
        onBack()
    }

    BackHandler {
        onBack()
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(24.dp),
            verticalArrangement = Arrangement.Top,
            horizontalAlignment = Alignment.Start
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { onBack() }) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "뒤로가기"
                    )
                }
                Text(
                    text = "내 번호 설정",
                    style = MaterialTheme.typography.headlineSmall
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "전화번호",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "숫자만 입력해주세요.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))

            AndroidView(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outline,
                        shape = RoundedCornerShape(12.dp)
                    )
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                factory = { viewContext ->
                    val editText = EditText(viewContext)
                    editText.inputType = InputType.TYPE_CLASS_PHONE
                    editText.setText(formatPhoneNumberForInput(inputNumber))
                    editText.setSelection(editText.text?.length ?: 0)
                    editText.addTextChangedListener(object : TextWatcher {
                        override fun afterTextChanged(s: Editable?) {
                            val digitsOnly = s?.toString()?.filter { it.isDigit() }.orEmpty()
                            if (digitsOnly != inputNumber) {
                                inputNumber = digitsOnly
                            }
                        }

                        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                            // no-op
                        }

                        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                            // no-op
                        }
                    })
                    editText
                },
                update = { editText ->
                    val formatted = formatPhoneNumberForInput(inputNumber)
                    if (editText.text?.toString() != formatted) {
                        editText.setText(formatted)
                        editText.setSelection(formatted.length)
                    }
                }
            )

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = { saveAndClose() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                Text("저장")
            }
        }
    }
}

private fun getDevicePhoneNumber(context: android.content.Context): String {
    val hasReadPhoneState = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.READ_PHONE_STATE
    ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    val hasReadPhoneNumbers = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.READ_PHONE_NUMBERS
    ) == android.content.pm.PackageManager.PERMISSION_GRANTED

    if (!hasReadPhoneState && !hasReadPhoneNumbers) {
        return ""
    }

    return try {
        val telephonyManager = context.getSystemService(android.content.Context.TELEPHONY_SERVICE) as? TelephonyManager
        val directNumber = telephonyManager?.line1Number.orEmpty()
        if (directNumber.isNotBlank()) {
            return directNumber.filter { it.isDigit() }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
            val subscriptionManager = context.getSystemService(android.content.Context.TELEPHONY_SUBSCRIPTION_SERVICE) as? SubscriptionManager
            val subscriptionNumber = subscriptionManager?.activeSubscriptionInfoList
                ?.firstOrNull { !it.number.isNullOrBlank() }
                ?.number
                .orEmpty()
            if (subscriptionNumber.isNotBlank()) {
                return subscriptionNumber.filter { it.isDigit() }
            }
        }

        ""
    } catch (e: SecurityException) {
        ""
    }
}

private fun formatPhoneNumberForInput(number: String): String {
    val digits = number.filter { it.isDigit() }
    if (digits.isEmpty()) {
        return ""
    }
    return when {
        digits.length <= 3 -> digits
        digits.length <= 7 -> "${digits.take(3)}-${digits.drop(3)}"
        else -> "${digits.take(3)}-${digits.drop(3).take(4)}-${digits.drop(7).take(4)}"
    }
}
