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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.res.painterResource
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.duckdns.dorandoran.callaiassistant.R
import org.duckdns.dorandoran.callaiassistant.ui.theme.CallaiassistantTheme

@Composable
fun IncomingCallScreen(
    modifier: Modifier = Modifier,
    callerName: String = "상대방",
    onAccept: () -> Unit,
    onReject: () -> Unit
) {
    val backgroundColor = Color(0xFFF8FBFF)
    val primaryTextColor = Color.Black
    val subtitleColor = Color(0xFF777B83)
    val phoneNumberColor = Color(0xFF73777F)
    val rejectColor = Color(0xFFEF3D3D)
    val acceptColor = Color(0xFF11C56F)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(backgroundColor)
            .navigationBarsPadding()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 83.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "T.mate 수신 중",
                color = subtitleColor,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(18.dp))

            Text(
                text = formatIncomingDisplay(callerName),
                color = primaryTextColor,
                fontSize = 30.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(9.dp))

            Text(
                text = "02-1233-2342",
                color = phoneNumberColor,
                fontSize = 15.sp,
                textAlign = TextAlign.Center
            )
        }

        Row(
            modifier = Modifier
                .width(296.dp)
                .padding(bottom = 32.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(73.dp)
                        .incomingButtonShadow()
                        .clip(CircleShape)
                        .background(rejectColor)
                        .clickable(onClick = onReject),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_call_end_solar),
                        contentDescription = "거절",
                        modifier = Modifier.fillMaxSize(0.7f),
                        tint = Color.White
                    )
                }
                Spacer(modifier = Modifier.height(11.dp))
                Text(
                    text = "거절",
                    color = primaryTextColor,
                    fontSize = 12.sp
                )
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(73.dp)
                        .incomingButtonShadow()
                        .clip(CircleShape)
                        .background(acceptColor)
                        .clickable(onClick = onAccept),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_call_accept_solar),
                        contentDescription = "응답",
                        modifier = Modifier.fillMaxSize(0.56f),
                        tint = Color.White
                    )
                }
                Spacer(modifier = Modifier.height(11.dp))
                Text(
                    text = "응답",
                    color = primaryTextColor,
                    fontSize = 12.sp
                )
            }
        }
    }
}

private fun Modifier.incomingButtonShadow() = this.drawBehind {
    val shadowColor = Color(0x4D959DA5).toArgb()
    val transparentColor = Color.Transparent.toArgb()

    drawIntoCanvas { canvas ->
        val paint = Paint()
        val frameworkPaint = paint.asFrameworkPaint()
        frameworkPaint.color = transparentColor
        frameworkPaint.setShadowLayer(
            24.dp.toPx(),
            0.dp.toPx(),
            8.dp.toPx(),
            shadowColor
        )
        canvas.drawCircle(
            center = center,
            radius = size.minDimension / 2f,
            paint = paint
        )
    }
}

private fun formatIncomingDisplay(raw: String): String {
    val digits = raw.filter { it.isDigit() }
    if (digits.isBlank()) return raw
    return when {
        digits.startsWith("02") && digits.length == 9 ->
            "${digits.take(2)}-${digits.drop(2).take(3)}-${digits.drop(5)}"
        digits.startsWith("02") && digits.length == 10 ->
            "${digits.take(2)}-${digits.drop(2).take(4)}-${digits.drop(6)}"
        digits.length == 10 ->
            "${digits.take(3)}-${digits.drop(3).take(3)}-${digits.drop(6)}"
        digits.length == 11 ->
            "${digits.take(3)}-${digits.drop(3).take(4)}-${digits.drop(7)}"
        else -> raw
    }
}

@Preview(showBackground = true)
@Composable
fun IncomingCallScreenPreview() {
    CallaiassistantTheme {
        IncomingCallScreen(
            callerName = "보라매 병원",
            onAccept = {},
            onReject = {}
        )
    }
}

