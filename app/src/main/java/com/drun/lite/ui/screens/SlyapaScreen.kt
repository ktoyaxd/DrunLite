package com.drun.lite.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.drun.lite.R
import com.drun.lite.ui.theme.DrunThemeColors
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

private data class SlyapaCloneState(
    var angle: Float,
    val radiusDp: Float,
    val speed: Float,
    val sizeDp: Float
)

@Composable
fun SlyapaScreen(
    balanceText: String,
    onBack: () -> Unit
) {
    var isInteracting by remember { mutableStateOf(false) }
    var rotX by remember { mutableFloatStateOf(0f) }
    var rotY by remember { mutableFloatStateOf(0f) }

    // 15 orbiting clones as in js/slyapa.js
    val clones = remember {
        List(15) {
            SlyapaCloneState(
                angle = Random.nextFloat() * (Math.PI.toFloat() * 2f),
                radiusDp = 60f + Random.nextFloat() * 40f,
                speed = 0.01f + Random.nextFloat() * 0.02f,
                sizeDp = 15f + Random.nextFloat() * 15f
            )
        }
    }

    var frameTick by remember { mutableLongStateOf(0L) }
    var speedMultiplier by remember { mutableFloatStateOf(1f) }

    LaunchedEffect(Unit) {
        var lastNanos = withFrameNanos { it }
        while (true) {
            val now = withFrameNanos { it }
            val delta = ((now - lastNanos) / 16_600_000f).coerceIn(0.1f, 3f)
            lastNanos = now

            val target = if (isInteracting) 5f else 1f
            speedMultiplier += (target - speedMultiplier) * 0.1f

            clones.forEach { c ->
                c.angle += c.speed * speedMultiplier * delta
            }
            frameTick = now
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "float")
    val floatOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -10f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "floatY"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DrunThemeColors.TgBg)
    ) {
        // .header
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
                text = "Шляпы",
                fontSize = 20.sp,
                fontWeight = FontWeight.Medium,
                color = DrunThemeColors.TgText
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // .slyapa-container (200x200)
            Box(
                modifier = Modifier
                    .size(200.dp)
                    .padding(bottom = 20.dp),
                contentAlignment = Alignment.Center
            ) {
                // Read frameTick so Compose updates orbit positions every frame
                val tick = frameTick
                val cloneAlpha = (0.2f + (speedMultiplier - 1f) * 0.2f).coerceIn(0.15f, 1f)

                clones.forEach { c ->
                    val xDp = cos(c.angle) * c.radiusDp
                    val yDp = sin(c.angle) * c.radiusDp
                    val density = androidx.compose.ui.platform.LocalDensity.current.density
                    Image(
                        painter = painterResource(R.drawable.gold_slyapa),
                        contentDescription = null,
                        modifier = Modifier
                            .offset {
                                IntOffset(
                                    (xDp * density).roundToInt(),
                                    (yDp * density).roundToInt()
                                )
                            }
                            .size(c.sizeDp.dp)
                            .alpha(if (tick >= 0L) cloneAlpha else cloneAlpha)
                            .graphicsLayer {
                                rotationZ = c.angle * 50f
                            }
                    )
                }

                // .slyapa-3d (140dp)
                Image(
                    painter = painterResource(R.drawable.gold_slyapa),
                    contentDescription = "Gold Slyapa",
                    modifier = Modifier
                        .width(140.dp)
                        .graphicsLayer {
                            translationY = if (isInteracting) 0f else floatOffset
                            rotationY = rotY
                            rotationX = rotX
                            cameraDistance = 12f * density
                        }
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = { isInteracting = true },
                                onDragEnd = {
                                    isInteracting = false
                                    rotX = 0f
                                    rotY = 0f
                                },
                                onDragCancel = {
                                    isInteracting = false
                                    rotX = 0f
                                    rotY = 0f
                                },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    rotY += dragAmount.x / 4f
                                    rotX -= dragAmount.y / 4f
                                }
                            )
                        }
                )
            }

            // #slyapaBalanceDisplay
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(bottom = 8.dp)
            ) {
                Image(
                    painter = painterResource(R.drawable.gold_slyapa),
                    contentDescription = null,
                    modifier = Modifier.size(32.dp)
                )
                Text(
                    text = balanceText,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = DrunThemeColors.TgText
                )
            }

            Text(
                text = "Ваш баланс",
                color = DrunThemeColors.TgTextSecondary,
                fontSize = 15.sp
            )
        }
    }
}
