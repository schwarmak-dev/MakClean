/**
 * MakClean - Gamified Gallery Cleaner
 * Developed by/Author: schwarmak-dev (https://github.com/schwarmak-dev)
 *
 * Copyright (c) 2026 schwarmak-dev. All rights reserved.
 */
package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.LocalAppColors

/**
 * Shown when the app has no access to the gallery. Explains why access is needed and lets the
 * user grant it (or open the system settings if they denied it permanently) — instead of
 * silently loading demo content, which looks broken to a real user.
 */
@Composable
fun PermissionScreen(
    onRequestPermission: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val appColors = LocalAppColors.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(appColors.gradientStart, appColors.gradientEnd)
                )
            )
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(28.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(CircleShape)
                    .background(appColors.primary.copy(alpha = 0.14f))
                    .border(1.dp, appColors.primary.copy(alpha = 0.35f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.PhotoLibrary,
                    contentDescription = null,
                    tint = appColors.primary,
                    modifier = Modifier.size(44.dp)
                )
            }

            Text(
                text = "Acceso a tu galería",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = appColors.textPrimary,
                textAlign = TextAlign.Center
            )

            Text(
                text = "MakClean necesita acceso a tus fotos y vídeos para encontrar duplicados, " +
                    "capturas y archivos pesados, y ayudarte a liberar espacio.\n\n" +
                    "Todo el análisis se hace en tu dispositivo: tus fotos no salen de él.",
                fontSize = 14.sp,
                color = appColors.textSecondary,
                textAlign = TextAlign.Center,
                lineHeight = 20.sp
            )

            Spacer(modifier = Modifier.height(4.dp))

            Button(
                onClick = onRequestPermission,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = appColors.primary,
                    contentColor = appColors.background
                ),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text(
                    text = "Permitir acceso",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            TextButton(onClick = onOpenSettings) {
                Text(
                    text = "Ya lo denegué — abrir ajustes",
                    fontSize = 13.sp,
                    color = appColors.textSecondary
                )
            }
        }
    }
}
