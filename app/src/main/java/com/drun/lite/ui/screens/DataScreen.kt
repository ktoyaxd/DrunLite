package com.drun.lite.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.drun.lite.data.CacheCategoryStat
import com.drun.lite.ui.theme.DrunThemeColors

@Composable
fun DataScreen(
    stats: List<CacheCategoryStat>,
    formatBytes: (Long) -> String,
    onClearCache: () -> Unit,
    onBack: () -> Unit
) {
    val totalSize = stats.sumOf { it.sizeBytes }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DrunThemeColors.TgBg)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .background(DrunThemeColors.HeaderBg)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Text("←", color = DrunThemeColors.TgText, fontSize = 22.sp)
            }
            Text(
                text = "Данные и память",
                fontSize = 20.sp,
                fontWeight = FontWeight.Medium,
                color = DrunThemeColors.TgText
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (totalSize == 0L) {
                Spacer(Modifier.height(40.dp))
                Text("📊", fontSize = 64.sp)
                Spacer(Modifier.height(16.dp))
                Text("Кеш пуст", fontSize = 18.sp, color = DrunThemeColors.TgText)
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Загрузите стикеры или фото, чтобы они появились здесь",
                    fontSize = 14.sp,
                    color = DrunThemeColors.TgTextSecondary,
                    textAlign = TextAlign.Center
                )
            } else {
                Spacer(Modifier.height(20.dp))
                val formatted = formatBytes(totalSize).split(" ")
                val numPart = formatted.getOrElse(0) { "0" }
                val unitPart = formatted.getOrElse(1) { "B" }

                Box(modifier = Modifier.size(160.dp), contentAlignment = Alignment.Center) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        var startAngle = -90f
                        stats.filter { it.sizeBytes > 0 }.forEach { cat ->
                            val sweep = (cat.sizeBytes.toFloat() / totalSize.toFloat()) * 360f
                            drawArc(
                                color = Color(cat.colorHex),
                                startAngle = startAngle,
                                sweepAngle = sweep,
                                useCenter = false,
                                style = Stroke(width = 20.dp.toPx(), cap = StrokeCap.Butt)
                            )
                            startAngle += sweep
                        }
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = numPart,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = DrunThemeColors.TgText
                        )
                        Text(
                            text = unitPart,
                            fontSize = 12.sp,
                            color = DrunThemeColors.TgTextSecondary
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))
                Text(
                    text = "Использование памяти",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                    color = DrunThemeColors.TgText
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "DrunLite занимает ${formatBytes(totalSize)}",
                    fontSize = 14.sp,
                    color = DrunThemeColors.TgTextSecondary
                )

                Spacer(Modifier.height(24.dp))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(DrunThemeColors.HeaderBg)
                ) {
                    val nonEmpty = stats.filter { it.sizeBytes > 0 }
                    nonEmpty.forEachIndexed { index, cat ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(Color(cat.colorHex)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("✓", color = Color.White, fontSize = 12.sp)
                                }
                                Spacer(Modifier.width(12.dp))
                                Text(cat.name, fontSize = 16.sp, color = DrunThemeColors.TgText)
                            }
                            Text(
                                text = formatBytes(cat.sizeBytes),
                                fontSize = 14.sp,
                                color = DrunThemeColors.TgTextSecondary
                            )
                        }
                        if (index < nonEmpty.lastIndex) {
                            HorizontalDivider(color = DrunThemeColors.SettingsItemBorder)
                        }
                    }
                }

                Spacer(Modifier.height(24.dp))
                Button(
                    onClick = onClearCache,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = DrunThemeColors.TgBtnBg),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Очистить кеш (${formatBytes(totalSize)})",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
            }
        }
    }
}
