package com.example.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*

@Composable
fun SuccessCelebration(
    freedBytes: Long,
    itemCount: Int,
    onFinish: () -> Unit
) {
    val appColors = LocalAppColors.current
    val scaleAnim = remember { Animatable(0f) }

    val freedString = remember(freedBytes) {
        val mb = freedBytes.toDouble() / (1024 * 1024)
        if (mb >= 1024) {
            String.format("%.2f GB", mb / 1024)
        } else {
            String.format("%.1f MB", mb)
        }
    }

    LaunchedEffect(Unit) {
        scaleAnim.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioLowBouncy,
                stiffness = Spring.StiffnessMediumLow
            )
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(appColors.gradientStart, appColors.gradientEnd)
                )
            )
            .padding(24.dp)
            .statusBarsPadding()
            .navigationBarsPadding(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(32.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            // Animated Badge
            Box(
                modifier = Modifier
                    .scale(scaleAnim.value)
                    .size(100.dp)
                    .clip(CircleShape)
                    .background(appColors.secondary.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.CheckCircle,
                    contentDescription = "Listo",
                    tint = appColors.secondary,
                    modifier = Modifier.size(48.dp)
                )
            }

            // Success Text block
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "LIMPIEZA COMPLETADA",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = appColors.secondary,
                    letterSpacing = 2.sp
                )

                Text(
                    text = freedString,
                    fontSize = 54.sp,
                    fontWeight = FontWeight.Black,
                    color = appColors.textPrimary,
                    letterSpacing = (-1.5).sp,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = "Movido a la papelera (recuperable ~30 días)",
                    fontSize = 14.sp,
                    color = appColors.textSecondary,
                    textAlign = TextAlign.Center
                )
            }

            // Breakdown card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(appColors.cardBg)
                    .padding(20.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "Elementos", color = appColors.textSecondary, fontSize = 14.sp)
                        Text(text = "$itemCount", color = appColors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "Tamaño", color = appColors.textSecondary, fontSize = 14.sp)
                        Text(text = freedString, color = appColors.secondary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                }
            }

            // CTA Button back to home
            Button(
                onClick = onFinish,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = appColors.primary,
                    contentColor = appColors.background
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(
                    text = "Escanear de nuevo",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
