package org.duckdns.dorandoran.callaiassistant.ui.screens

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.duckdns.dorandoran.callaiassistant.R

// ▼▼▼ [중요] 이미 존재하는 Activity 파일들을 Import 합니다 ▼▼▼
// 패키지명이 다르면 빨간줄이 뜰 수 있습니다. 그럴 땐 Alt+Enter로 Import 해주세요.
import org.duckdns.dorandoran.callaiassistant.ServiceTermsActivity
import org.duckdns.dorandoran.callaiassistant.PrivacyCollectionActivity
import org.duckdns.dorandoran.callaiassistant.PrivacyThirdPartyActivity
import org.duckdns.dorandoran.callaiassistant.CallTextProcessingActivity

// 스타일 정의 (Figma)
private object FigmaStyle {
    val Pretendard = FontFamily(
        Font(R.font.pretendard, FontWeight.Normal),
        Font(R.font.pretendard_medium, FontWeight.Medium),
        Font(R.font.pretendard_semibold, FontWeight.SemiBold),
        Font(R.font.pretendard_bold, FontWeight.Bold)
    )

    val MainNavy = Color(0xFF323683)
    val BlackText = Color(0xFF000000)
    val Blue = Color(0xFF4C6FFF)
    val GrayText = Color(0xFF858C9F)
    val BoxBg = Color(0xFFF4F5F9)

    val HeaderBaseStyle = androidx.compose.ui.text.TextStyle(
        fontFamily = Pretendard,
        fontSize = 29.sp,
        lineHeight = 37.sp,
        fontWeight = FontWeight.Bold
    )

    val ItemTitleStyle = androidx.compose.ui.text.TextStyle(
        fontFamily = Pretendard,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        lineHeight = 20.sp,
        color = GrayText
    )

    val ItemDescStyle = androidx.compose.ui.text.TextStyle(
        fontFamily = Pretendard,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 18.sp,
        color = GrayText
    )
}

// Data Class: 이동할 Activity 클래스 정보를 담도록 수정
data class ConsentItem(
    val title: String,
    val description: String,
    val checked: Boolean,
    val detailActivity: Class<*>? = null // ★ 이동할 Activity 클래스
)

@Composable
fun OnboardingPermissionsScreen(
    onAgreeComplete: () -> Unit
) {
    val context = LocalContext.current

    // ▼▼▼ [핵심] 기존 Activity들을 여기에 연결합니다 ▼▼▼
    val items = remember {
        mutableStateListOf(
            ConsentItem(
                "(필수) 서비스 이용약관 동의",
                "서비스 이용에 필요한 기본 규칙과 책임 규정",
                false,
                detailActivity = ServiceTermsActivity::class.java // 연결
            ),
            ConsentItem(
                "(필수) 개인정보 수집 및 이용 동의",
                "본인 확인 및 원활한 서비스 운영에 필요",
                false,
                detailActivity = PrivacyCollectionActivity::class.java // 연결
            ),
            ConsentItem(
                "(필수) 개인정보 제3자 제공 동의",
                "AI 답변 생성 및 발음 보정 처리에 필요",
                false,
                detailActivity = PrivacyThirdPartyActivity::class.java // 연결
            ),
            ConsentItem(
                "(필수) 통화 내용 텍스트 변환 및 처리 동의",
                "실시간 통화 음성을 텍스트로 변환하는 데 필요",
                false,
                detailActivity = CallTextProcessingActivity::class.java // 연결
            )
        )
    }

    val allChecked = items.all { it.checked }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Spacer(modifier = Modifier.height(56.dp))

        // 헤더
        Text(
            text = buildAnnotatedString {
                withStyle(style = SpanStyle(color = FigmaStyle.MainNavy)) { append("T.mate \n") }
                withStyle(style = SpanStyle(color = FigmaStyle.BlackText)) { append("원활한 통화를 위해\n약관에 동의가 필요합니다") }
            },
            style = FigmaStyle.HeaderBaseStyle,
            modifier = Modifier.width(290.dp)
        )

        Spacer(modifier = Modifier.height(40.dp))

        // 전체 동의 박스
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(FigmaStyle.BoxBg)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    val newState = !allChecked
                    items.indices.forEach { items[it] = items[it].copy(checked = newState) }
                }
                .padding(horizontal = 15.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = if (allChecked) FigmaStyle.Blue else Color(0xFFDDDDDD),
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = "필수 약관 모두 동의",
                fontFamily = FigmaStyle.Pretendard,
                fontWeight = FontWeight.SemiBold,
                fontSize = 17.sp,
                color = FigmaStyle.BlackText
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // 개별 약관 리스트
        Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
            items.forEachIndexed { index, item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            items[index] = items[index].copy(checked = !item.checked)
                        }
                        .padding(horizontal = 15.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = if (item.checked) FigmaStyle.Blue else Color(0xFFDDDDDD),
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(text = item.title, style = FigmaStyle.ItemTitleStyle)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(text = item.description, style = FigmaStyle.ItemDescStyle)
                    }

                    // ★★★ [여기] 화살표 클릭 시 기존 Activity로 이동하는 코드 ★★★
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clickable {
                                // DetailActivity가 등록되어 있다면 Intent로 이동
                                if (item.detailActivity != null) {
                                    val intent = Intent(context, item.detailActivity)
                                    context.startActivity(intent)
                                }
                            },
                        contentAlignment = Alignment.CenterEnd
                    ) {
                        Text(
                            text = "›",
                            fontSize = 24.sp,
                            color = Color(0xFFDDDDDD)
                        )
                    }
                }

                if (index < items.lastIndex) Spacer(modifier = Modifier.height(12.dp))
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // 버튼
        Button(
            onClick = onAgreeComplete,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            enabled = allChecked,
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = FigmaStyle.Blue,
                disabledContainerColor = Color(0xFFE5E5EA),
                contentColor = Color.White,
                disabledContentColor = Color(0xFFC7C7CC)
            ),
            elevation = ButtonDefaults.buttonElevation(0.dp)
        ) {
            Text(text = "다음", fontFamily = FigmaStyle.Pretendard, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.height(24.dp))
    }
}