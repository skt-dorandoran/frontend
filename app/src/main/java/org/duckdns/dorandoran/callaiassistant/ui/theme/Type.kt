package org.duckdns.dorandoran.callaiassistant.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import org.duckdns.dorandoran.callaiassistant.R

val PretendardFamily = FontFamily(
    Font(R.font.pretendard_thin, FontWeight.Thin),
    Font(R.font.pretendard_extralight, FontWeight.ExtraLight),
    Font(R.font.pretendard_light, FontWeight.Light),
    Font(R.font.pretendard, FontWeight.Normal),
    Font(R.font.pretendard_medium, FontWeight.Medium),
    Font(R.font.pretendard_semibold, FontWeight.SemiBold),
    Font(R.font.pretendard_bold, FontWeight.Bold),
    Font(R.font.pretendard_extrabold, FontWeight.ExtraBold),
    Font(R.font.pretendard_black, FontWeight.Black)
)

// Apply Pretendard to all text styles by default.
private val BaseTypography = Typography()

val Typography = Typography(
    displayLarge = BaseTypography.displayLarge.copy(fontFamily = PretendardFamily),
    displayMedium = BaseTypography.displayMedium.copy(fontFamily = PretendardFamily),
    displaySmall = BaseTypography.displaySmall.copy(fontFamily = PretendardFamily),
    headlineLarge = BaseTypography.headlineLarge.copy(fontFamily = PretendardFamily),
    headlineMedium = BaseTypography.headlineMedium.copy(fontFamily = PretendardFamily),
    headlineSmall = BaseTypography.headlineSmall.copy(fontFamily = PretendardFamily),
    titleLarge = BaseTypography.titleLarge.copy(fontFamily = PretendardFamily),
    titleMedium = BaseTypography.titleMedium.copy(fontFamily = PretendardFamily),
    titleSmall = BaseTypography.titleSmall.copy(fontFamily = PretendardFamily),
    bodyLarge = BaseTypography.bodyLarge.copy(fontFamily = PretendardFamily),
    bodyMedium = BaseTypography.bodyMedium.copy(fontFamily = PretendardFamily),
    bodySmall = BaseTypography.bodySmall.copy(fontFamily = PretendardFamily),
    labelLarge = BaseTypography.labelLarge.copy(fontFamily = PretendardFamily),
    labelMedium = BaseTypography.labelMedium.copy(fontFamily = PretendardFamily),
    labelSmall = BaseTypography.labelSmall.copy(fontFamily = PretendardFamily)
)