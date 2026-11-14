package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

private val FinbossLightColorScheme =
  lightColorScheme(
    primary = NavyPrimary,
    onPrimary = Color.White,
    primaryContainer = NavyContainer,
    onPrimaryContainer = OnNavyContainer,
    secondary = EmeraldSecondary,
    onSecondary = Color.White,
    secondaryContainer = EmeraldContainer,
    onSecondaryContainer = OnEmeraldContainer,
    tertiary = AmberTertiary,
    onTertiary = Color.White,
    tertiaryContainer = AmberContainer,
    onTertiaryContainer = OnAmberContainer,
    background = SurfaceLight,
    onBackground = OnSurfaceLight,
    surface = SurfaceCardLight,
    onSurface = OnSurfaceLight,
    surfaceVariant = SurfaceVariantLight,
    onSurfaceVariant = OnSurfaceMutedLight,
    outline = OutlineLight,
    error = ErrorCrimson,
    errorContainer = ErrorContainerLight,
  )

private val FinbossDarkColorScheme =
  darkColorScheme(
    primary = NavyPrimaryDark,
    onPrimary = OnNavyDark,
    primaryContainer = NavyContainerDark,
    onPrimaryContainer = OnNavyContainerDark,
    secondary = EmeraldSecondaryDark,
    onSecondary = OnEmeraldDark,
    secondaryContainer = EmeraldContainerDark,
    onSecondaryContainer = OnEmeraldContainerDark,
    tertiary = AmberTertiaryDark,
    onTertiary = OnAmberDark,
    tertiaryContainer = AmberContainerDark,
    onTertiaryContainer = OnAmberContainerDark,
    background = SurfaceDark,
    onBackground = OnSurfaceDark,
    surface = SurfaceCardDark,
    onSurface = OnSurfaceDark,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = OnSurfaceMutedDark,
    outline = OutlineDark,
    error = ErrorCrimsonDark,
    errorContainer = ErrorContainerDark,
  )

val FinbossShapes =
  Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
  )

@Composable
fun FinbossErpTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
) {
  val colorScheme =
    when {
      dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
        val context = LocalContext.current
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
      }
      darkTheme -> FinbossDarkColorScheme
      else -> FinbossLightColorScheme
    }

  MaterialTheme(
    colorScheme = colorScheme,
    typography = FinbossTypography,
    shapes = FinbossShapes,
    content = content,
  )
}
