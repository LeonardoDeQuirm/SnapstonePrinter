package dev.snapstonewielder.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import androidx.compose.ui.unit.sp
import dev.snapstonewielder.app.R

private val googleFontProvider = GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage = "com.google.android.gms",
    certificates = R.array.com_google_android_gms_fonts_certs
)

/**
 * Display typeface for headline-scale text only (card names, sheet titles, the app bar) - a real
 * typographic choice instead of stock system Roboto everywhere. Fetched at runtime via Play
 * Services; Compose falls back to the platform default automatically if that ever fails (e.g. no
 * Play Services on the device), so this can never crash the app.
 */
private val displayGoogleFont = GoogleFont("Fraunces")

val DisplayFontFamily = FontFamily(
    Font(googleFont = displayGoogleFont, fontProvider = googleFontProvider, weight = FontWeight.SemiBold),
    Font(googleFont = displayGoogleFont, fontProvider = googleFontProvider, weight = FontWeight.Bold)
)

// Material's own baseline sizes/weights/line-heights per level - only fontFamily is overridden
// below, so the type SCALE stays the well-tested Material default and only the LOOK changes.
private val baseline = Typography()

val Typography = Typography(
    displayLarge = baseline.displayLarge.copy(fontFamily = DisplayFontFamily),
    displayMedium = baseline.displayMedium.copy(fontFamily = DisplayFontFamily),
    displaySmall = baseline.displaySmall.copy(fontFamily = DisplayFontFamily),
    headlineLarge = baseline.headlineLarge.copy(fontFamily = DisplayFontFamily),
    headlineMedium = baseline.headlineMedium.copy(fontFamily = DisplayFontFamily),
    headlineSmall = baseline.headlineSmall.copy(fontFamily = DisplayFontFamily),
    titleLarge = baseline.titleLarge.copy(fontFamily = DisplayFontFamily),
    bodyLarge = baseline.bodyLarge.copy(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp
    )
)
