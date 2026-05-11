package com.example.babyallergycheck.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val AppColorScheme: ColorScheme = lightColorScheme(
    primary = Color(0xFF28666E),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD5F2F0),
    onPrimaryContainer = Color(0xFF063D43),
    secondary = Color(0xFF8A5A44),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFE0D0),
    onSecondaryContainer = Color(0xFF4A2718),
    tertiary = Color(0xFF557A35),
    tertiaryContainer = Color(0xFFDDECC8),
    background = Color(0xFFFFFBF7),
    surface = Color(0xFFFFFBF7),
    surfaceVariant = Color(0xFFECE4DC),
    outline = Color(0xFF85746D),
)

@Composable
fun BabyAllergyTheme(
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = AppColorScheme,
        typography = MaterialTheme.typography,
        content = content,
    )
}
