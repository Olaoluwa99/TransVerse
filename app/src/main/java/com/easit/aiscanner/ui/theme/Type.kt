package com.easit.aiscanner.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.easit.aiscanner.R

/**
 * Figtree, a friendly geometric sans (SIL Open Font License, see assets/licenses). One variable
 * font file covers every weight: each [Font] sets the font's weight axis to its [FontWeight].
 */
val Figtree = FontFamily(
    Font(R.font.figtree_variable, FontWeight.Normal),
    Font(R.font.figtree_variable, FontWeight.Medium),
    Font(R.font.figtree_variable, FontWeight.SemiBold),
    Font(R.font.figtree_variable, FontWeight.Bold),
    Font(R.font.figtree_variable, FontWeight.ExtraBold),
)

/**
 * Figtree throughout: it stays readable at small sizes, so body text and translations use it
 * too. Large styles get a heavier weight for Material 3 Expressive's bolder hierarchy.
 */
internal val TransVerseTypography: Typography = Typography().run {
    Typography(
        displayLarge = displayLarge.copy(fontFamily = Figtree, fontWeight = FontWeight.SemiBold),
        displayMedium = displayMedium.copy(fontFamily = Figtree, fontWeight = FontWeight.SemiBold),
        displaySmall = displaySmall.copy(fontFamily = Figtree, fontWeight = FontWeight.SemiBold),
        headlineLarge = headlineLarge.copy(fontFamily = Figtree, fontWeight = FontWeight.SemiBold),
        headlineMedium = headlineMedium.copy(fontFamily = Figtree, fontWeight = FontWeight.SemiBold),
        headlineSmall = headlineSmall.copy(fontFamily = Figtree, fontWeight = FontWeight.SemiBold),
        titleLarge = titleLarge.copy(fontFamily = Figtree, fontWeight = FontWeight.SemiBold),
        titleMedium = titleMedium.copy(fontFamily = Figtree, fontWeight = FontWeight.Medium),
        titleSmall = titleSmall.copy(fontFamily = Figtree, fontWeight = FontWeight.Medium),
        bodyLarge = bodyLarge.copy(fontFamily = Figtree),
        bodyMedium = bodyMedium.copy(fontFamily = Figtree),
        bodySmall = bodySmall.copy(fontFamily = Figtree),
        labelLarge = labelLarge.copy(fontFamily = Figtree, fontWeight = FontWeight.SemiBold),
        labelMedium = labelMedium.copy(fontFamily = Figtree, fontWeight = FontWeight.SemiBold),
        labelSmall = labelSmall.copy(fontFamily = Figtree, fontWeight = FontWeight.Medium),
    )
}

/** The user's chosen size for text being read and translated (Settings → Reading text size). */
val LocalReadingTextSize = staticCompositionLocalOf<TextUnit> { 16.sp }

/**
 * Style for transcripts and translations. Only this text follows the in-app size setting;
 * everything else follows the system font scale.
 */
@Composable
@ReadOnlyComposable
fun readingTextStyle(): TextStyle {
    val size = LocalReadingTextSize.current
    return MaterialTheme.typography.bodyLarge.copy(fontSize = size, lineHeight = size * 1.5f)
}
