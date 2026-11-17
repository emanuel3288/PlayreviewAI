package com.appinsight.ai.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Light = lightColorScheme(primary = Color(0xFF4361EE), secondary = Color(0xFF7950F2), tertiary = Color(0xFF18C8C0), surface = Color(0xFFF7F9FC), error = Color(0xFFEF4444))
private val Dark = darkColorScheme(primary = Color(0xFF9BABFF), secondary = Color(0xFFB5A4FF), tertiary = Color(0xFF5CE1DA), background = Color(0xFF0B1020), surface = Color(0xFF151C30), error = Color(0xFFFFB4AB))

@Composable fun AppInsightTheme(dark: Boolean = true, content: @Composable () -> Unit) { MaterialTheme(colorScheme = if (dark) Dark else Light, content = content) }
