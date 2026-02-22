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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
        color = Color.White
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
            verticalArrangement = Arrangement.Top,
            horizontalAlignment = Alignment.Start
        ) {
            // 상단 헤더 - 설정 화면과 동일한 스타일
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { onBack() },
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
                    text = "내 번호 설정",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 18.sp
                    ),
                    color = Color.Black
                )
            }

            // 콘텐츠 영역
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
            ) {
                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "전화번호",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF1A1A1A)
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "숫자만 입력해주세요.",
                    fontSize = 13.sp,
                    color = Color(0xFF999999)
                )
                Spacer(modifier = Modifier.height(16.dp))

                // 입력 필드 - 앱 스타일에 맞춘 연한 회색 배경 + 라운드
                AndroidView(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFFF5F5F5))
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    factory = { viewContext ->
                        val editText = EditText(viewContext)
                        editText.inputType = InputType.TYPE_CLASS_PHONE
                        editText.setText(formatPhoneNumberForInput(inputNumber))
                        editText.setSelection(editText.text?.length ?: 0)
                        editText.background = null
                        editText.setSelectAllOnFocus(true)
                        editText.setTextColor(android.graphics.Color.parseColor("#1A1A1A"))
                        editText.textSize = 16f
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

                Spacer(modifier = Modifier.height(28.dp))

                // 저장 버튼 - 권한동의 화면의 "다음" 버튼과 동일한 스타일
                Button(
                    onClick = { saveAndClose() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF4B7BF5)
                    ),
                    elevation = ButtonDefaults.buttonElevation(
                        defaultElevation = 0.dp,
                        pressedElevation = 0.dp
                    )
                ) {
                    Text(
                        text = "저장",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                }
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
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val modernNumber = subscriptionManager?.activeSubscriptionInfoList
                    ?.asSequence()
                    ?.mapNotNull { info ->
                        subscriptionManager.getPhoneNumber(info.subscriptionId)
                            ?.filter { it.isDigit() }
                            ?.takeIf { it.isNotBlank() }
                    }
                    ?.firstOrNull()
                    .orEmpty()
                if (modernNumber.isNotBlank()) {
                    return modernNumber
                }
            }

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
