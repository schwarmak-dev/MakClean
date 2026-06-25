package com.example.ui.theme

import android.content.Context
import android.content.ContextWrapper
import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.example.ui.MakCleanTheme

private fun Context.findActivity(): Activity? {
    var context = this
    while (context is ContextWrapper) {
        if (context is Activity) {
            return context
        }
        context = context.baseContext
    }
    return null
}

data class AppColors(
    val primary: Color,
    val secondary: Color,
    val tertiary: Color,
    val background: Color,
    val cardBg: Color,
    val outline: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val accentGold: Color,
    val gradientStart: Color,
    val gradientEnd: Color
)

val LocalAppColors = staticCompositionLocalOf {
    AppColors(
        primary = Color(0xFF00E5FF),
        secondary = Color(0xFF10B981),
        tertiary = Color(0xFFF43F5E),
        background = Color(0xFF060608),
        cardBg = Color(0xFF111115),
        outline = Color(0xFF23232C),
        textPrimary = Color(0xFFFFFFFF),
        textSecondary = Color(0xFF8E8E9F),
        textMuted = Color(0xFF555562),
        accentGold = Color(0xFFF59E0B),
        gradientStart = Color(0xFF0B0F19),
        gradientEnd = Color(0xFF04060A)
    )
}

fun getThemeColors(theme: MakCleanTheme): AppColors {
    return when (theme) {
        MakCleanTheme.NEON_CYBER -> AppColors(
            primary = Color(0xFF00E5FF), // Electric cyan
            secondary = Color(0xFF10B981), // KeepGreen
            tertiary = Color(0xFFF43F5E), // TrashRed
            background = Color(0xFF060608),
            cardBg = Color(0xFF111115),
            outline = Color(0xFF23232C),
            textPrimary = Color(0xFFFFFFFF),
            textSecondary = Color(0xFF8E8E9F),
            textMuted = Color(0xFF555562),
            accentGold = Color(0xFFF59E0B),
            gradientStart = Color(0xFF0D1525),
            gradientEnd = Color(0xFF060912)
        )
        MakCleanTheme.WARM_ORGANIC -> AppColors(
            primary = Color(0xFFE07A5F), // Earthy Terracotta
            secondary = Color(0xFF10B981), 
            tertiary = Color(0xFFF43F5E), 
            background = Color(0xFF1B1B18), // Cozy Charcoal
            cardBg = Color(0xFF242421), // Soft Clay
            outline = Color(0xFF383832), 
            textPrimary = Color(0xFFF4F1DE), // Soft Cream
            textSecondary = Color(0xFFBEBBAE), // Sand Gray
            textMuted = Color(0xFF7E7C74),
            accentGold = Color(0xFF81B29A), // Warm Sage
            gradientStart = Color(0xFF2C2C28),
            gradientEnd = Color(0xFF171715)
        )
        MakCleanTheme.NORDIC_ROSE -> AppColors(
            primary = Color(0xFFF48C96), // Nordic Rose
            secondary = Color(0xFF10B981),
            tertiary = Color(0xFFF43F5E),
            background = Color(0xFF121419), // Dark Lavender Twilight
            cardBg = Color(0xFF1A1D24), 
            outline = Color(0xFF2D313C),
            textPrimary = Color(0xFFF1F3F7), // Pure Ice
            textSecondary = Color(0xFFA5B0C2), // Mist Blue
            textMuted = Color(0xFF6E788A),
            accentGold = Color(0xFFC39797), 
            gradientStart = Color(0xFF1F232D),
            gradientEnd = Color(0xFF0E1014)
        )
        MakCleanTheme.CLASSIC_SLATE -> AppColors(
            primary = Color(0xFF60A5FA), // Steel Blue
            secondary = Color(0xFF10B981),
            tertiary = Color(0xFFF43F5E),
            background = Color(0xFF0F172A), // Charcoal Slate
            cardBg = Color(0xFF1E293B), // Navy Graphite
            outline = Color(0xFF334155),
            textPrimary = Color(0xFFF8FAFC),
            textSecondary = Color(0xFF94A3B8), 
            textMuted = Color(0xFF64748B),
            accentGold = Color(0xFFD97706), 
            gradientStart = Color(0xFF1E293B),
            gradientEnd = Color(0xFF090D16)
        )
    }
}

@Composable
fun MyApplicationTheme(
    activeTheme: MakCleanTheme = MakCleanTheme.CLASSIC_SLATE,
    content: @Composable () -> Unit
) {
    val themeColors = getThemeColors(activeTheme)
    
    val m3ColorScheme = darkColorScheme(
        primary = themeColors.primary,
        secondary = themeColors.secondary,
        tertiary = themeColors.tertiary,
        background = themeColors.background,
        surface = themeColors.cardBg,
        onPrimary = themeColors.background,
        onSecondary = themeColors.background,
        onTertiary = themeColors.background,
        onBackground = themeColors.textPrimary,
        onSurface = themeColors.textPrimary,
        outline = themeColors.outline
    )

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            view.context.findActivity()?.window?.let { window ->
                // Edge-to-edge: the system bars stay transparent and the app draws behind
                // them, so we only keep their icons light (every MakClean theme is dark).
                val controller = WindowCompat.getInsetsController(window, view)
                controller.isAppearanceLightStatusBars = false
                controller.isAppearanceLightNavigationBars = false
            }
        }
    }

    androidx.compose.runtime.CompositionLocalProvider(
        LocalAppColors provides themeColors
    ) {
        MaterialTheme(
            colorScheme = m3ColorScheme,
            typography = Typography,
            content = content
        )
    }
}
