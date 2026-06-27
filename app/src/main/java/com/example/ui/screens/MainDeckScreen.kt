/**
 * MakClean - Gamified Gallery Cleaner
 * Developed by/Author: schwarmak-dev (https://github.com/schwarmak-dev)
 * 
 * Copyright (c) 2026 schwarmak-dev. All rights reserved.
 */
package com.example.ui.screens

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import android.util.Log
import android.widget.VideoView
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.HeartBroken
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import coil.compose.AsyncImage
import com.example.media.MediaFilterType
import com.example.media.MediaItem
import com.example.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

import com.example.ui.DeckSortOrder

@Composable
fun MainDeckScreen(
    remainingItems: List<MediaItem>,
    trashedCount: Int,
    trashedBytes: Long,
    keptCount: Int,
    favoritedCount: Int,
    privacyActive: Boolean,
    onTogglePrivacy: () -> Unit,
    selectedFilter: MediaFilterType,
    currentSortOrder: DeckSortOrder,
    onSortOrderChanged: (DeckSortOrder) -> Unit,
    onSwipeKeep: (MediaItem) -> Unit,
    onSwipeTrash: (MediaItem) -> Unit,
    onSwipeFavorite: (MediaItem) -> Unit,
    onUndo: () -> Unit,
    canUndo: Boolean,
    onEmptyTrash: () -> Unit,
    onBackToFilters: () -> Unit,
    trashedItems: List<MediaItem>,
    onRestoreFromTrash: (MediaItem) -> Unit,
    onDeletePermanentlyFromTrash: (MediaItem) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val appColors = LocalAppColors.current

    // Animate counts elegantly
    val trashMBRepresentation = remember(trashedBytes) {
        val mb = trashedBytes.toDouble() / (1024 * 1024)
        if (mb >= 1024) String.format("%.2f GB", mb / 1024) else String.format("%.1f MB", mb)
    }

    var showTrashSheet by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(appColors.background)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 1. TOP BAR PANELS
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IconButton(
                        onClick = onBackToFilters,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(appColors.cardBg)
                            .border(1.dp, appColors.outline, CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ChevronLeft,
                            contentDescription = "Filtros",
                            tint = appColors.textPrimary
                        )
                    }

                    Column {
                        Text(
                            text = when (selectedFilter) {
                                MediaFilterType.ALL -> "Galería General"
                                MediaFilterType.SCREENSHOTS -> "Capturas"
                                MediaFilterType.HEAVY_VIDEOS -> "Vídeos Pesados"
                                MediaFilterType.DUPLICATES -> "Duplicados"
                                MediaFilterType.BLURRY -> "Fotos Borrosas"
                                MediaFilterType.GIFS -> "GIFs"
                                MediaFilterType.LIGHT_VIDEOS -> "Vídeos Ligeros"
                            },
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = appColors.textPrimary
                        )
                        Text(
                            text = if (remainingItems.isEmpty()) "Revisión completada"
                                   else "${remainingItems.size} por revisar",
                            fontSize = 11.sp,
                            color = appColors.textSecondary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                // Privacy Switch Buttons
                IconButton(
                    onClick = onTogglePrivacy,
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(if (privacyActive) appColors.primary.copy(alpha = 0.18f) else appColors.cardBg)
                        .border(
                            1.dp,
                            if (privacyActive) appColors.primary else appColors.outline,
                            CircleShape
                        )
                ) {
                    Icon(
                        imageVector = if (privacyActive) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                        contentDescription = "Toggle Privacy",
                        tint = if (privacyActive) appColors.primary else appColors.textSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // 2. STATS FLOATING BANNER
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(appColors.cardBg)
                    .border(BorderStroke(1.dp, appColors.outline), RoundedCornerShape(12.dp))
                    .clickable { showTrashSheet = true }
                    .padding(vertical = 9.dp, horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(TrashRed)
                    )
                    Text(
                        text = "Papelera $trashedCount",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = appColors.textPrimary
                    )
                }

                Text(
                    text = "Recuperas $trashMBRepresentation",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = appColors.textSecondary
                )

                Text(
                    text = "Fav $favoritedCount",
                    fontSize = 12.sp,
                    color = appColors.textSecondary
                )
            }

            // DECK SORTING SELECTOR PILLS
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val sortingOptions = listOf(
                        DeckSortOrder.LARGEST_FIRST to "Tamaño",
                        DeckSortOrder.NEWEST_FIRST to "Recientes",
                        DeckSortOrder.OLDEST_FIRST to "Antiguas"
                    )

                    sortingOptions.forEach { (order, label) ->
                        val isSelected = currentSortOrder == order
                        val primary = LocalAppColors.current.primary
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isSelected) primary.copy(alpha = 0.12f) else LocalAppColors.current.cardBg)
                                .border(
                                    width = 1.dp,
                                    color = if (isSelected) primary else LocalAppColors.current.outline,
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .clickable { onSortOrderChanged(order) }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) primary else LocalAppColors.current.textPrimary
                            )
                        }
                    }
                }
            }

            // 3. INTERACTIVE STACK OF CARDS
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(vertical = 6.dp, horizontal = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                if (remainingItems.isEmpty()) {
                    // Pile Empty visualizer
                    EmptyTrashPrompt(
                        trashedCount = trashedCount,
                        recoveryText = trashMBRepresentation,
                        onPurge = onEmptyTrash
                    )
                } else {
                    // Preload and depth layer 2 cards underneath
                    val maxPreloaded = 3
                    val renderable = remainingItems.take(maxPreloaded).reversed()

                    renderable.forEachIndexed { idx, item ->
                        key(item.id) {
                            val isFront = (item.id == remainingItems.first().id)
                            
                            // Stack depth animations for pre-cards
                            val depthIndex = remainingItems.indexOf(item) // 0 is front, 1 is layer-1, 2 is layer-2
                            val visualScale = when (depthIndex) {
                                0 -> 1.0f
                                1 -> 0.95f
                                else -> 0.90f
                            }
                            val visualOffset = when (depthIndex) {
                                0 -> 0.dp
                                1 -> 12.dp
                                else -> 24.dp
                            }

                            if (isFront) {
                                SwipableCard(
                                    item = item,
                                    privacyActive = privacyActive,
                                    onSwipeLeft = { onSwipeTrash(item) },
                                    onSwipeRight = { onSwipeKeep(item) },
                                    onSwipeUp = { onSwipeFavorite(item) }
                                )
                            } else {
                                // Sub-layers (locked backdrop static display cards)
                                StaticDeckCard(
                                    item = item,
                                    modifier = Modifier
                                        .graphicsLayer {
                                            scaleX = visualScale
                                            scaleY = visualScale
                                        }
                                        .offset(y = visualOffset)
                                )
                            }
                        }
                    }
                }

                // Fixed overlay (does NOT move with the card): subtle bottom gradient + media
                // info + action buttons, layered over the bottom of the front card so the
                // image/video can use the full height.
                if (remainingItems.isNotEmpty()) {
                    val front = remainingItems.first()
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(0.96f)
                            .clip(RoundedCornerShape(24.dp)),
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f))
                                    )
                                )
                                .padding(horizontal = 20.dp, vertical = 14.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(1.dp)
                            ) {
                                Text(
                                    text = front.title,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = if (front.isVideo && front.durationText.isNotEmpty())
                                        "${front.sizeText} · ${front.durationText}"
                                    else front.sizeText,
                                    fontSize = 12.sp,
                                    color = Color.White.copy(alpha = 0.75f)
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                DeckActionButton(
                                    icon = Icons.AutoMirrored.Filled.Undo,
                                    contentDescription = "Deshacer",
                                    tint = if (canUndo) Color.White else Color.White.copy(alpha = 0.4f),
                                    size = 48.dp,
                                    enabled = canUndo,
                                    outline = appColors.outline,
                                    surface = appColors.cardBg,
                                    onClick = onUndo
                                )
                                DeckActionButton(
                                    icon = Icons.Filled.Close,
                                    contentDescription = "Borrar",
                                    tint = appColors.tertiary,
                                    size = 60.dp,
                                    outline = appColors.outline,
                                    surface = appColors.cardBg,
                                    testTag = "swipe_left_button",
                                    onClick = { onSwipeTrash(front) }
                                )
                                DeckActionButton(
                                    icon = Icons.Outlined.StarBorder,
                                    contentDescription = "Favorito",
                                    tint = appColors.primary,
                                    size = 48.dp,
                                    outline = appColors.outline,
                                    surface = appColors.cardBg,
                                    onClick = { onSwipeFavorite(front) }
                                )
                                DeckActionButton(
                                    icon = Icons.Filled.Favorite,
                                    contentDescription = "Conservar",
                                    tint = appColors.secondary,
                                    size = 60.dp,
                                    outline = appColors.outline,
                                    surface = appColors.cardBg,
                                    testTag = "swipe_right_button",
                                    onClick = { onSwipeKeep(front) }
                                )
                            }
                        }
                    }
                }
            }
        }

        // SLIDE-UP TRASH BIN PANEL
        AnimatedVisibility(
            visible = showTrashSheet,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.7f))
                    .clickable { showTrashSheet = false },
                contentAlignment = Alignment.BottomCenter
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(0.75f)
                        .clickable(enabled = false) {}, // Prevent dismiss when clicking the list
                    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                    colors = CardDefaults.cardColors(containerColor = LocalAppColors.current.cardBg),
                    border = BorderStroke(1.dp, LocalAppColors.current.outline)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Header
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Papelera",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = LocalAppColors.current.textPrimary
                                )
                                Text(
                                    text = "$trashedCount elementos seleccionados • Recuperas en total $trashMBRepresentation",
                                    fontSize = 12.sp,
                                    color = LocalAppColors.current.textSecondary
                                )
                            }
                            
                            IconButton(
                                onClick = { showTrashSheet = false },
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(LocalAppColors.current.outline, CircleShape)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Cerrar",
                                    tint = LocalAppColors.current.textPrimary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        Text(
                            text = "Estos elementos se moverán a la papelera del sistema, donde podrás recuperarlos durante unos 30 días. Pulsa 'Restaurar' si cambias de opinión antes de confirmar.",
                            fontSize = 11.sp,
                            color = LocalAppColors.current.textMuted,
                            lineHeight = 15.sp
                        )

                        // Empty State inside slide-up recycler
                        if (trashedItems.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.padding(horizontal = 24.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.DeleteForever,
                                        contentDescription = null,
                                        tint = LocalAppColors.current.primary.copy(alpha = 0.5f),
                                        modifier = Modifier.size(48.dp)
                                    )
                                    Text(
                                        text = "La papelera está vacía",
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = LocalAppColors.current.textPrimary,
                                        textAlign = TextAlign.Center
                                    )
                                    Text(
                                        text = "Desliza fotos hacia la izquierda si quieres removerlas de tu galería. Cada elemento que limpies recuperará espacio real en tu móvil.",
                                        fontSize = 12.sp,
                                        color = LocalAppColors.current.textSecondary,
                                        textAlign = TextAlign.Center,
                                        lineHeight = 16.sp
                                    )
                                }
                            }
                        } else {
                            // Elements Recycler List
                            LazyColumn(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(trashedItems) { item ->
                                    val appColors = LocalAppColors.current
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(appColors.cardBg)
                                            .border(1.dp, appColors.outline.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                                            .padding(8.dp),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // Media thumbnail
                                        Box(
                                            modifier = Modifier
                                                .size(64.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(appColors.outline),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            AsyncImage(
                                                model = item.uri.toString(),
                                                contentDescription = null,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                            if (item.isVideo) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(24.dp)
                                                        .background(Color.Black.copy(alpha = 0.6f), CircleShape),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Filled.PlayArrow,
                                                        contentDescription = null,
                                                        tint = Color.White,
                                                        modifier = Modifier.size(12.dp)
                                                    )
                                                }
                                            }
                                        }

                                        // Metadata Column
                                        Column(
                                            modifier = Modifier.weight(1f),
                                            verticalArrangement = Arrangement.spacedBy(2.dp)
                                        ) {
                                            Text(
                                                text = item.title,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = appColors.textPrimary,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = item.sizeText,
                                                fontSize = 11.sp,
                                                color = appColors.textSecondary,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            // Badges row
                                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                if (item.isDuplicate) {
                                                    Text(
                                                        text = "Duplicada",
                                                        fontSize = 9.sp,
                                                        color = appColors.textMuted,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                                if (item.isBlurred) {
                                                    Text(
                                                        text = "Borrosa",
                                                        fontSize = 9.sp,
                                                        color = appColors.textMuted,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                            }
                                        }

                                        // Action buttons
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            // Restore button (KeepGreen-colored Text Button / icon)
                                            TextButton(
                                                onClick = { onRestoreFromTrash(item) },
                                                colors = ButtonDefaults.textButtonColors(contentColor = appColors.primary),
                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.AutoMirrored.Filled.Undo,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(14.dp)
                                                    )
                                                    Text("Restaurar", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                }
                                            }

                                            // Permanent Delete Button
                                            IconButton(
                                                onClick = { onDeletePermanentlyFromTrash(item) },
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Outlined.DeleteForever,
                                                    contentDescription = "Mover a la papelera",
                                                    tint = appColors.tertiary.copy(alpha = 0.85f),
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Complete Action button
                        if (trashedItems.isNotEmpty()) {
                            Button(
                                onClick = {
                                    showTrashSheet = false
                                    onEmptyTrash()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = TrashRed),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(
                                    text = "Mover a la papelera",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// Interactive Draggable Deck card with multi-axis swings & color glow edges
@Composable
fun SwipableCard(
    item: MediaItem,
    privacyActive: Boolean,
    onSwipeLeft: () -> Unit,
    onSwipeRight: () -> Unit,
    onSwipeUp: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val appColors = LocalAppColors.current
    // Semantic swipe colors, sourced from the active theme (keep / favorite / delete).
    val keepColor = appColors.secondary
    val favoriteColor = appColors.primary
    val deleteColor = appColors.tertiary
    val configuration = LocalConfiguration.current
    val screenWidth = with(LocalDensity.current) { configuration.screenWidthDp.dp.toPx() }
    val screenHeight = with(LocalDensity.current) { configuration.screenHeightDp.dp.toPx() }

    // Coordinates physics mapping
    var dragX by remember(item.id) { mutableStateOf(0f) }
    var dragY by remember(item.id) { mutableStateOf(0f) }

    val swipeThreshold = screenWidth * 0.35f

    // Rotation multiplier based on drag offset
    val rotationAngle = (dragX / screenWidth) * 20f

    // Multi-axis glow intensities
    val keepGlow = (dragX / swipeThreshold).coerceIn(0f, 1f)
    val deleteGlow = (-dragX / swipeThreshold).coerceIn(0f, 1f)
    val favorGlow = (-dragY / (screenHeight * 0.25f)).coerceIn(0f, 1f)

    var scale by remember(item.id) { mutableStateOf(1f) }
    var offset by remember(item.id) { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
    var revealByHold by remember(item.id) { mutableStateOf(false) }
    var isVideoPausedGlobally by remember(item.id) { mutableStateOf(false) }
    var isZoomed by remember(item.id) { mutableStateOf(false) }

    val displayBlur = privacyActive && !revealByHold

    val transformState = rememberTransformableState { zoomChange, offsetChange, _ ->
        if (!item.isVideo && !displayBlur) {
            scale = (scale * zoomChange).coerceIn(1f, 5f)
            if (scale > 1f) {
                offset += offsetChange
            } else {
                offset = androidx.compose.ui.geometry.Offset.Zero
            }
            isZoomed = scale > 1f
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.96f)
            .graphicsLayer {
                translationX = dragX
                translationY = dragY
                rotationZ = rotationAngle
            }
            .transformable(state = transformState)
            .pointerInput(item.id, isZoomed) {
                if (!isZoomed) {
                    detectDragGestures(
                        onDragEnd = {
                            if (dragX > swipeThreshold) {
                                scope.launch {
                                    animate(initialValue = dragX, targetValue = screenWidth, animationSpec = tween(150)) { value, _ ->
                                        dragX = value
                                    }
                                    onSwipeRight()
                                }
                            } else if (dragX < -swipeThreshold) {
                                scope.launch {
                                    animate(initialValue = dragX, targetValue = -screenWidth, animationSpec = tween(150)) { value, _ ->
                                        dragX = value
                                    }
                                    onSwipeLeft()
                                }
                            } else if (dragY < -swipeThreshold) {
                                scope.launch {
                                    animate(initialValue = dragY, targetValue = -screenHeight, animationSpec = tween(150)) { value, _ ->
                                        dragY = value
                                    }
                                    onSwipeUp()
                                }
                            } else {
                                scope.launch {
                                    launch {
                                        animate(initialValue = dragX, targetValue = 0f, animationSpec = spring(dampingRatio = 0.75f)) { value, _ ->
                                            dragX = value
                                        }
                                    }
                                    launch {
                                        animate(initialValue = dragY, targetValue = 0f, animationSpec = spring(dampingRatio = 0.75f)) { value, _ ->
                                            dragY = value
                                        }
                                    }
                                }
                            }
                        },
                        onDragCancel = {
                            scope.launch {
                                launch {
                                    animate(initialValue = dragX, targetValue = 0f, animationSpec = spring()) { value, _ ->
                                        dragX = value
                                    }
                                }
                                launch {
                                    animate(initialValue = dragY, targetValue = 0f, animationSpec = spring()) { value, _ ->
                                        dragY = value
                                    }
                                }
                            }
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            dragX += dragAmount.x
                            dragY += dragAmount.y
                        }
                    )
                }
            }
            .pointerInput(item.id, displayBlur) {
                detectTapGestures(
                    onDoubleTap = {
                        if (!item.isVideo && !displayBlur) {
                            scale = if (scale > 1f) 1f else 2.5f
                            if (scale == 1f) {
                                offset = androidx.compose.ui.geometry.Offset.Zero
                            }
                            isZoomed = scale > 1f
                        }
                    },
                    onPress = {
                        try {
                            revealByHold = true
                            isVideoPausedGlobally = true
                            awaitRelease()
                        } catch (e: Exception) {
                            if (e is kotlinx.coroutines.CancellationException) {
                                throw e
                            }
                        } finally {
                            revealByHold = false
                            isVideoPausedGlobally = false
                        }
                    }
                )
            },
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = appColors.cardBg),
        border = BorderStroke(1.dp, appColors.outline)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {

            // Render media card contents
            CardMediaContents(
                item = item,
                privacyActive = privacyActive,
                scale = scale,
                offset = offset,
                revealByHold = revealByHold,
                isVideoPausedGlobally = isVideoPausedGlobally
            )

            // Directional edge glow while dragging (keep / right)
            if (keepGlow > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .drawBehind {
                            val brush = Brush.horizontalGradient(
                                colors = listOf(Color.Transparent, keepColor.copy(alpha = keepGlow * 0.25f)),
                                startX = size.width * 0.6f,
                                endX = size.width
                            )
                            drawRect(brush)
                        }
                        .border(BorderStroke(4.dp * keepGlow, keepColor.copy(alpha = keepGlow)), RoundedCornerShape(24.dp))
                )
            }

            // Directional edge glow while dragging (delete / left)
            if (deleteGlow > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .drawBehind {
                            val brush = Brush.horizontalGradient(
                                colors = listOf(deleteColor.copy(alpha = deleteGlow * 0.25f), Color.Transparent),
                                startX = 0f,
                                endX = size.width * 0.4f
                            )
                            drawRect(brush)
                        }
                        .border(BorderStroke(4.dp * deleteGlow, deleteColor.copy(alpha = deleteGlow)), RoundedCornerShape(24.dp))
                )
            }

            // Directional edge glow while dragging (favorite / up)
            if (favorGlow > 0f && dragY < 0) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .drawBehind {
                            val brush = Brush.verticalGradient(
                                colors = listOf(favoriteColor.copy(alpha = favorGlow * 0.25f), Color.Transparent),
                                startY = 0f,
                                endY = size.height * 0.4f
                            )
                            drawRect(brush)
                        }
                        .border(BorderStroke(4.dp * favorGlow, favoriteColor.copy(alpha = favorGlow)), RoundedCornerShape(24.dp))
                )
            }

            // Action Indicator Labels overlaid on corners
            if (keepGlow > 0.2f) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(24.dp)
                        .graphicsLayer { rotationZ = -12f }
                        .border(BorderStroke(3.dp, keepColor), RoundedCornerShape(8.dp))
                        .background(keepColor.copy(alpha = 0.1f))
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text(text = "CONSERVAR", color = keepColor, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                }
            }

            if (deleteGlow > 0.2f) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(24.dp)
                        .graphicsLayer { rotationZ = 12f }
                        .border(BorderStroke(3.dp, deleteColor), RoundedCornerShape(8.dp))
                        .background(deleteColor.copy(alpha = 0.1f))
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text(text = "BORRAR", color = deleteColor, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                }
            }

            if (favorGlow > 0.2f && dragY < 0) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 80.dp)
                        .border(BorderStroke(3.dp, favoriteColor), RoundedCornerShape(8.dp))
                        .background(favoriteColor.copy(alpha = 0.1f))
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text(text = "FAVORITO", color = favoriteColor, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                }
            }
        }
    }
}

// Background layer card decoration to visually communicate depth and physics
@Composable
fun StaticDeckCard(
    item: MediaItem,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .fillMaxHeight(0.96f),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = DarkCardBg),
        border = BorderStroke(1.dp, SlateOutline.copy(alpha = 0.6f))
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            AsyncImage(
                model = item.uri.toString(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(30.dp) // Blur locked cards to signify background depth
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.40f))
            )
        }
    }
}

// Displays individual media contents, supports privacy blur toggles and metadata HUDs
@Composable
fun CardMediaContents(
    item: MediaItem,
    privacyActive: Boolean,
    scale: Float,
    offset: androidx.compose.ui.geometry.Offset,
    revealByHold: Boolean,
    isVideoPausedGlobally: Boolean
) {
    val displayBlur = privacyActive && !revealByHold

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(24.dp))
    ) {
        if (item.isVideo) {
            // Render video player with mute & loop configurations
            Box(
                modifier = Modifier.fillMaxSize()
            ) {
                // Looping media view
                key(item.id) {
                    LoopingVideoPlayer(
                        videoUrl = item.uri.toString(),
                        isPaused = isVideoPausedGlobally
                    )
                }
            }
        } else {
            // Photos use Fit so the whole frame is visible (no cropping); pinch-to-zoom still works.
            AsyncImage(
                model = item.uri.toString(),
                contentDescription = item.title,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offset.x
                        translationY = offset.y
                    }
            )
        }

        // Privacy Modo frosted glass banner overlay (Feature 5)
        if (displayBlur) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.7f))
                    .blur(20.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(SlateOutline),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.VisibilityOff,
                            contentDescription = "Hidden",
                            tint = FavoriteBlue,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    Text(
                        text = "Modo Privacidad Activo",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextWhite
                    )

                    Text(
                        text = "Mantén presionado para revelar el contenido de forma segura",
                        fontSize = 12.sp,
                        color = TextGray,
                        textAlign = TextAlign.Center,
                        lineHeight = 16.sp
                    )
                }
            }
        }

        // Media metadata is shown below the card (see MainDeckScreen) so it never covers the image.
    }
}

// Looper Video player leveraging native light framework VideoView + MediaPlayer safely with dynamic error and mock fallback
@Composable
fun LoopingVideoPlayer(
    videoUrl: String,
    isPaused: Boolean
) {
    var hasError by remember(videoUrl) { mutableStateOf(false) }

    // If it is a web URL, we prefer simulating it to avoid cleartext/HTTPS slow network load hangs or codec issues in host container
    val isMockVideo = videoUrl.startsWith("http://") || videoUrl.startsWith("https://")

    if (hasError || isMockVideo) {
        SimulatedVideoPlayer(videoUrl = videoUrl, isPaused = isPaused)
    } else {
        Box(modifier = Modifier.fillMaxSize()) {
            AndroidView(
                factory = { ctx ->
                    VideoView(ctx).apply {
                        try {
                            setOnErrorListener { _, _, _ ->
                                hasError = true
                                true
                            }
                            setVideoURI(Uri.parse(videoUrl))
                            setOnPreparedListener { mp ->
                                try {
                                    mp.isLooping = true
                                    mp.setVolume(0f, 0f) // Keep muted strictly as requested
                                    if (!isPaused) {
                                        start()
                                    }
                                } catch (t: Throwable) {
                                    Log.e("LoopingVideoPlayer", "OnPrepared error: ${t.message}")
                                }
                            }
                        } catch (t: Throwable) {
                            Log.e("LoopingVideoPlayer", "VideoView factory initiation failure: ${t.message}")
                            hasError = true
                        }
                    }
                },
                update = { videoView ->
                    try {
                        videoView.setOnErrorListener { _, _, _ ->
                            hasError = true
                            true
                        }
                        if (isPaused) {
                            if (videoView.isPlaying) {
                                videoView.pause()
                            }
                        } else {
                            if (!videoView.isPlaying) {
                                videoView.start()
                            }
                        }
                    } catch (t: Throwable) {
                        Log.e("LoopingVideoPlayer", "VideoView update failure: ${t.message}")
                    }
                },
                onRelease = { videoView ->
                    try {
                        videoView.stopPlayback()
                    } catch (t: Throwable) {
                        Log.e("LoopingVideoPlayer", "VideoView release failure: ${t.message}")
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

// Visual simulation of video player for sandbox environments and codec errors
@Composable
fun SimulatedVideoPlayer(
    videoUrl: String,
    isPaused: Boolean
) {
    val infiniteTransition = rememberInfiniteTransition(label = "VideoSimPulse")
    
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "SimulatedPulse"
    )

    val rotatingAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(5000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "SimulatedRotation"
    )

    var mockProgress by remember { mutableStateOf(0f) }
    LaunchedEffect(isPaused) {
        if (!isPaused) {
            while (true) {
                delay(30)
                mockProgress = (mockProgress + 0.004f) % 1.0f
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        DeepSlateBg.copy(alpha = 0.85f),
                        DarkCardBg,
                        DeepSlateBg.copy(alpha = 0.95f)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.PlayArrow,
            contentDescription = "Video",
            tint = AccentGold.copy(alpha = 0.12f),
            modifier = Modifier
                .size(160.dp)
                .scale(pulseScale)
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(24.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(72.dp)
                    .graphicsLayer { rotationZ = if (!isPaused) rotatingAngle else 0f }
            ) {
                CircularProgressIndicator(
                    progress = { 0.75f },
                    modifier = Modifier.fillMaxSize(),
                    color = AccentGold,
                    strokeWidth = 3.dp,
                    trackColor = SlateOutline.copy(alpha = 0.2f),
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(SlateOutline.copy(alpha = 0.4f))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(if (isPaused) TextMuted else Color.Red)
                    )
                    Text(
                        text = if (isPaused) "PAUSADO" else "REPRODUCIENDO",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isPaused) TextMuted else TextWhite,
                        letterSpacing = 0.8.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth(0.55f)
                    .height(3.dp)
                    .clip(CircleShape)
                    .background(SlateOutline.copy(alpha = 0.3f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(mockProgress)
                        .fillMaxHeight()
                        .background(AccentGold)
                )
            }
        }
    }
}

// Sober circular action button used in the deck toolbar: neutral surface, thin outline,
// no neon glow — the color lives only in the icon tint.
@Composable
fun DeckActionButton(
    icon: ImageVector,
    contentDescription: String,
    tint: Color,
    size: Dp,
    surface: Color,
    outline: Color,
    enabled: Boolean = true,
    testTag: String? = null,
    onClick: () -> Unit
) {
    val base = Modifier
        .size(size)
        .clip(CircleShape)
        .background(surface)
        .border(1.dp, outline, CircleShape)
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = if (testTag != null) base.testTag(testTag) else base
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(size * 0.42f)
        )
    }
}

// Full screen empty queue prompter guiding empty action
@Composable
fun EmptyTrashPrompt(
    trashedCount: Int,
    recoveryText: String,
    onPurge: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(TrashRed.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.DeleteForever,
                contentDescription = "Vaciar",
                tint = TrashRed,
                modifier = Modifier.size(40.dp)
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "Revisión completada",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = TextWhite
            )

            Text(
                text = "Se moverán $trashedCount elemento(s) a la papelera del sistema. Podrás recuperarlos durante unos 30 días.",
                fontSize = 13.sp,
                color = TextGray,
                textAlign = TextAlign.Center,
                lineHeight = 18.sp
            )
        }

        Button(
            onClick = onPurge,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .testTag("empty_trash_button"),
            colors = ButtonDefaults.buttonColors(
                containerColor = TrashRed,
                contentColor = Color.White
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.DeleteForever,
                    contentDescription = null,
                    tint = Color.White
                )
                Text(
                    text = "Mover a la papelera ($recoveryText)",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
