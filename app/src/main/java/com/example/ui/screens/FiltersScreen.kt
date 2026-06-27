/**
 * MakClean - Gamified Gallery Cleaner
 * Developed by/Author: schwarmak-dev (https://github.com/schwarmak-dev)
 * 
 * Copyright (c) 2026 schwarmak-dev. All rights reserved.
 */
package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.media.MediaFilterType
import com.example.ui.theme.*
import com.example.ui.MakCleanTheme

import com.example.ui.StorageAnalytics
import com.example.ui.StorageCategoryStats
import androidx.compose.foundation.shape.CircleShape

private fun formatBytes(bytes: Long): String {
    val mb = bytes.toDouble() / (1024 * 1024)
    return if (mb >= 1024) {
        String.format("%.2f GB", mb / 1024)
    } else {
        String.format("%.1f MB", mb)
    }
}

@Composable
fun StorageAnalyticsPanel(
    analytics: StorageAnalytics?,
    onCategoryClick: (MediaFilterType) -> Unit
) {
    val appColors = LocalAppColors.current
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp)),
        colors = CardDefaults.cardColors(containerColor = appColors.cardBg),
        border = BorderStroke(1.dp, appColors.outline)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "ESPACIO RECUPERABLE",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = appColors.accentGold,
                        letterSpacing = 0.5.sp
                    )
                    Text(
                        text = "Estimación por categorías",
                        fontSize = 12.sp,
                        color = appColors.textSecondary
                    )
                }
                
                if (analytics != null) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(KeepGreen.copy(alpha = 0.15f))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = formatBytes(analytics.totalCleanableBytes),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = KeepGreen
                        )
                    }
                } else {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = appColors.primary,
                        strokeWidth = 1.5.dp
                    )
                }
            }

            if (analytics != null) {
                val totalBytesOfCategories = analytics.categories.sumOf { it.sizeBytes }

                // Segmented Progress Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(appColors.outline)
                ) {
                    if (totalBytesOfCategories == 0L) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth()
                                .background(appColors.textSecondary.copy(alpha = 0.3f))
                        )
                    } else {
                        analytics.categories.forEach { category ->
                            val weight = category.sizeBytes.toFloat() / totalBytesOfCategories
                            if (weight > 0f) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .weight(weight)
                                        .background(Color(category.colorHex))
                                )
                            }
                        }
                    }
                }

                // Interactive columns (tighter grid setup)
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val rows = analytics.categories.chunked(2)
                    rows.forEach { rowCategories ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            rowCategories.forEach { category ->
                                val categoryName = when (category.type) {
                                    MediaFilterType.DUPLICATES -> "Duplicados"
                                    MediaFilterType.HEAVY_VIDEOS -> "Vídeos Pesados"
                                    MediaFilterType.SCREENSHOTS -> "Capturas"
                                    MediaFilterType.BLURRY -> "Borrosas"
                                    MediaFilterType.GIFS -> "GIFs"
                                    MediaFilterType.LIGHT_VIDEOS -> "Vídeos Ligeros"
                                    else -> "Otros"
                                }
                                
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(appColors.background.copy(alpha = 0.5f))
                                        .clickable { onCategoryClick(category.type) }
                                        .padding(horizontal = 8.dp, vertical = 6.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .clip(CircleShape)
                                            .background(Color(category.colorHex))
                                    )
                                    Column {
                                        Text(
                                            text = categoryName,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = appColors.textPrimary
                                        )
                                        Text(
                                            text = "${category.count} archs. • ${formatBytes(category.sizeBytes)}",
                                            fontSize = 9.sp,
                                            color = appColors.textSecondary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                Spacer(modifier = Modifier.height(6.dp))
            }
        }
    }
}

@Composable
fun FiltersScreen(
    selectedFilter: MediaFilterType,
    onFilterSelected: (MediaFilterType) -> Unit,
    analytics: StorageAnalytics?,
    activeTheme: MakCleanTheme = MakCleanTheme.CLASSIC_SLATE,
    onThemeSelected: (MakCleanTheme) -> Unit = {},
    onStartScan: () -> Unit
) {
    val scrollState = rememberScrollState()
    val appColors = LocalAppColors.current
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(appColors.gradientStart, appColors.gradientEnd)
                )
            )
            .statusBarsPadding()
            .navigationBarsPadding(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Branded Compact Header Row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 2.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(appColors.primary.copy(alpha = 0.14f))
                            .border(1.dp, appColors.primary.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
                            .padding(8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        MakCleanLogo(
                            color = appColors.primary,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    Text(
                        text = "MakClean",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = appColors.textPrimary,
                        letterSpacing = (-0.5).sp
                    )
                }

                // Piola Theme Selector: Small rounded circle dots
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(end = 4.dp)
                ) {
                    MakCleanTheme.values().forEach { theme ->
                        val isSelected = activeTheme == theme
                        val themeColors = getThemeColors(theme)
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .clip(CircleShape)
                                .background(if (isSelected) themeColors.primary.copy(alpha = 0.25f) else Color.Transparent)
                                .border(
                                    width = if (isSelected) 1.5.dp else 0.5.dp,
                                    color = if (isSelected) themeColors.primary else appColors.textMuted,
                                    shape = CircleShape
                                )
                                .clickable { onThemeSelected(theme) }
                                .padding(3.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(themeColors.primary)
                            )
                        }
                    }
                }
            }

            Text(
                text = "Revisa y libera espacio en tu galería",
                fontSize = 12.sp,
                color = appColors.textSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 4.dp)
            )



            StorageAnalyticsPanel(
                analytics = analytics,
                onCategoryClick = { category ->
                    onFilterSelected(category)
                }
            )

            // Selector title
            Text(
                text = "¿QUÉ QUIERES REVISAR?",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = AccentGold,
                letterSpacing = 0.5.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp, bottom = 2.dp),
                textAlign = TextAlign.Start
            )

            // Compact Filter Buttons Grid
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterCard(
                        title = "Duplicados",
                        subtitle = "Copias idénticas",
                        icon = Icons.Outlined.ContentCopy,
                        isSelected = selectedFilter == MediaFilterType.DUPLICATES,
                        modifier = Modifier.weight(1f),
                        onClick = { onFilterSelected(MediaFilterType.DUPLICATES) }
                    )

                    FilterCard(
                        title = "Vídeos Pesados",
                        subtitle = "Más de 15 MB",
                        icon = Icons.Outlined.VideoLibrary,
                        isSelected = selectedFilter == MediaFilterType.HEAVY_VIDEOS,
                        modifier = Modifier.weight(1f),
                        onClick = { onFilterSelected(MediaFilterType.HEAVY_VIDEOS) }
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterCard(
                        title = "Capturas",
                        subtitle = "Capturas de pantalla",
                        icon = Icons.Outlined.Crop,
                        isSelected = selectedFilter == MediaFilterType.SCREENSHOTS,
                        modifier = Modifier.weight(1f),
                        onClick = { onFilterSelected(MediaFilterType.SCREENSHOTS) }
                    )

                    FilterCard(
                        title = "GIFs",
                        subtitle = "Archivos .gif",
                        icon = Icons.Outlined.Gif,
                        isSelected = selectedFilter == MediaFilterType.GIFS,
                        modifier = Modifier.weight(1f),
                        onClick = { onFilterSelected(MediaFilterType.GIFS) }
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterCard(
                        title = "Vídeos Ligeros",
                        subtitle = "Vídeos pequeños",
                        icon = Icons.Outlined.Movie,
                        isSelected = selectedFilter == MediaFilterType.LIGHT_VIDEOS,
                        modifier = Modifier.weight(1f),
                        onClick = { onFilterSelected(MediaFilterType.LIGHT_VIDEOS) }
                    )

                    FilterCard(
                        title = "Borrosas",
                        subtitle = "Fotos desenfocadas",
                        icon = Icons.Outlined.BlurOn,
                        isSelected = selectedFilter == MediaFilterType.BLURRY,
                        modifier = Modifier.weight(1f),
                        onClick = { onFilterSelected(MediaFilterType.BLURRY) }
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterCard(
                        title = "Analizar Todo",
                        subtitle = "Toda la galería",
                        icon = Icons.Outlined.PhotoLibrary,
                        isSelected = selectedFilter == MediaFilterType.ALL,
                        modifier = Modifier.weight(1f),
                        onClick = { onFilterSelected(MediaFilterType.ALL) }
                    )

                    // Keeps the last row aligned with the two-column grid above.
                    Spacer(modifier = Modifier.weight(1f))
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Start Scanning Button
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Button(
                    onClick = onStartScan,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = appColors.primary,
                        contentColor = appColors.background
                    ),
                    shape = RoundedCornerShape(10.dp),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Escanear Galería",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Text(
                    text = "El análisis se realiza en tu dispositivo",
                    fontSize = 10.sp,
                    color = appColors.textMuted,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Desarrollado por schwarmak-dev • github.com/schwarmak-dev",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = appColors.textSecondary.copy(alpha = 0.8f),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

// Custom MakClean brand mark: a minimalist broom, drawn with Canvas so it scales crisply
// and carries the app's identity instead of a generic stock icon.
@Composable
fun MakCleanLogo(
    color: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        fun p(fx: Float, fy: Float) = Offset(w * fx, h * fy)

        // Handle: a rounded diagonal stroke from the upper-right down toward the head.
        drawLine(
            color = color,
            start = p(0.78f, 0.12f),
            end = p(0.46f, 0.52f),
            strokeWidth = size.minDimension * 0.11f,
            cap = StrokeCap.Round
        )

        // Broom head: a solid quadrilateral fanning out perpendicular to the handle.
        val head = Path().apply {
            moveTo(w * 0.38f, h * 0.42f)
            lineTo(w * 0.58f, h * 0.60f)
            lineTo(w * 0.36f, h * 0.90f)
            lineTo(w * 0.10f, h * 0.70f)
            close()
        }
        drawPath(head, color)
    }
}

@Composable
fun FilterCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    isSelected: Boolean,
    badgeColor: Color? = null,
    badgeText: String? = null,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val appColors = LocalAppColors.current
    val duration = 300
    val borderAlpha by animateFloatAsState(
        targetValue = if (isSelected) 1f else 0.1f,
        animationSpec = tween(duration)
    )
    val glowIntensity by animateFloatAsState(
        targetValue = if (isSelected) 0.15f else 0f,
        animationSpec = tween(duration)
    )

    Card(
        modifier = modifier
            .drawBehind {
                if (glowIntensity > 0f) {
                    drawRoundRect(
                        color = appColors.primary.copy(alpha = glowIntensity),
                        size = size,
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(12.dp.toPx())
                    )
                }
            }
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) appColors.primary.copy(alpha = 0.12f) else appColors.cardBg
        ),
        border = BorderStroke(
            width = if (isSelected) 1.5.dp else 1.dp,
            color = if (isSelected) appColors.primary else appColors.outline
        )
    ) {
        Box(
            modifier = Modifier
                .padding(10.dp)
                .fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isSelected) appColors.primary.copy(alpha = 0.15f) else appColors.outline)
                            .padding(6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = title,
                            tint = if (isSelected) appColors.primary else appColors.textPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    if (isSelected) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = "Selected",
                            tint = appColors.primary,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }

                Column(
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = title,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = appColors.textPrimary
                    )

                    Text(
                        text = subtitle,
                        fontSize = 10.sp,
                        color = appColors.textSecondary,
                        lineHeight = 13.sp
                    )
                }
            }

            if (badgeText != null && badgeColor != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 4.dp, y = (-8).dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(badgeColor)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = badgeText,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = appColors.background
                    )
                }
            }
        }
    }
}
