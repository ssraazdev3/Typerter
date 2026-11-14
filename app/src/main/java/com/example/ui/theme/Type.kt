package com.example.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.model.TypewriterFont

val SpecialEliteFamily = FontFamily(Font(R.font.special_elite, FontWeight.Normal))
val CourierPrimeFamily = FontFamily(Font(R.font.courier_prime, FontWeight.Normal))
val IbmPlexMonoFamily = FontFamily(Font(R.font.ibm_plex_mono, FontWeight.Normal))
val SpaceMonoFamily = FontFamily(Font(R.font.space_mono, FontWeight.Normal))
val NewsreaderFamily = FontFamily(Font(R.font.newsreader, FontWeight.Normal))
val InterFamily = FontFamily(Font(R.font.inter, FontWeight.Normal))

fun TypewriterFont.toComposeFontFamily(): FontFamily = when (this) {
    TypewriterFont.SPECIAL_ELITE -> SpecialEliteFamily
    TypewriterFont.COURIER_PRIME -> CourierPrimeFamily
    TypewriterFont.IBM_PLEX_MONO -> IbmPlexMonoFamily
    TypewriterFont.SPACE_MONO -> SpaceMonoFamily
    TypewriterFont.NEWSREADER -> NewsreaderFamily
    TypewriterFont.INTER -> InterFamily
}

val AppTypography = Typography(
    displaySmall = TextStyle(
        fontFamily = SpecialEliteFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 30.sp,
        lineHeight = 36.sp
    ),
    headlineSmall = TextStyle(
        fontFamily = CourierPrimeFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 22.sp,
        lineHeight = 28.sp
    ),
    titleMedium = TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.sp
    ),
    labelMedium = TextStyle(
        fontFamily = IbmPlexMonoFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.4.sp
    ),
    labelSmall = TextStyle(
        fontFamily = IbmPlexMonoFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.3.sp
    )
)
