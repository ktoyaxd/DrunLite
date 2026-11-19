package com.drun.lite.ui.components

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.ByteArrayOutputStream
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.roundToInt

@Composable
fun CropperModal(
    sourceBitmap: Bitmap,
    onCancel: () -> Unit,
    onConfirmBytes: (ByteArray) -> Unit
) {
    var rotationDeg by remember { mutableFloatStateOf(0f) }
    var areaW by remember { mutableFloatStateOf(0f) }
    var areaH by remember { mutableFloatStateOf(0f) }

    // Canvas dimensions fitted to area (maxHeight 60% like setupCanvas in cropper.js)
    val canvasW: Float
    val canvasH: Float
    if (areaW > 0f && areaH > 0f) {
        var w = sourceBitmap.width.toFloat()
        var h = sourceBitmap.height.toFloat()
        if (w > areaW) {
            h *= (areaW / w)
            w = areaW
        }
        if (h > areaH) {
            w *= (areaH / h)
            h = areaH
        }
        canvasW = w
        canvasH = h
    } else {
        canvasW = 1f
        canvasH = 1f
    }

    var rectX by remember(canvasW, canvasH) { mutableFloatStateOf(0f) }
    var rectY by remember(canvasW, canvasH) { mutableFloatStateOf(0f) }
    var rectW by remember(canvasW, canvasH) { mutableFloatStateOf(canvasW) }
    var rectH by remember(canvasW, canvasH) { mutableFloatStateOf(canvasH) }

    var activeHandle by remember { mutableStateOf<String?>(null) }

    val imageBitmap = remember(sourceBitmap) { sourceBitmap.asImageBitmap() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // .crop-header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.8f))
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onCancel) {
                Text("ОТМЕНА", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            }
            TextButton(onClick = {
                val cropped = performCropAndCompress(
                    src = sourceBitmap,
                    rotation = rotationDeg,
                    canvasW = canvasW,
                    canvasH = canvasH,
                    rx = rectX,
                    ry = rectY,
                    rw = rectW,
                    rh = rectH
                )
                onConfirmBytes(cropped)
            }) {
                Text("ГОТОВО", color = Color(0xFF229AF0), fontSize = 16.sp, fontWeight = FontWeight.Medium)
            }
        }

        // .crop-canvas-area
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .onSizeChanged {
                    areaW = it.width.toFloat()
                    areaH = it.height.toFloat()
                },
            contentAlignment = Alignment.Center
        ) {
            if (canvasW > 1f && canvasH > 1f) {
                Canvas(
                    modifier = Modifier
                        .size(
                            width = (canvasW / androidx.compose.ui.platform.LocalDensity.current.density).dp,
                            height = (canvasH / androidx.compose.ui.platform.LocalDensity.current.density).dp
                        )
                        .pointerInput(canvasW, canvasH) {
                            detectDragGestures(
                                onDragStart = { offset ->
                                    val edge = 48f
                                    val x = offset.x
                                    val y = offset.y
                                    activeHandle = when {
                                        hypot(x - rectX, y - rectY) < edge -> "tl"
                                        hypot(x - (rectX + rectW), y - rectY) < edge -> "tr"
                                        hypot(x - rectX, y - (rectY + rectH)) < edge -> "bl"
                                        hypot(x - (rectX + rectW), y - (rectY + rectH)) < edge -> "br"
                                        x in rectX..(rectX + rectW) && y in rectY..(rectY + rectH) -> "move"
                                        else -> null
                                    }
                                },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    val dx = dragAmount.x
                                    val dy = dragAmount.y
                                    when (activeHandle) {
                                        "move" -> {
                                            rectX = (rectX + dx).coerceIn(0f, canvasW - rectW)
                                            rectY = (rectY + dy).coerceIn(0f, canvasH - rectH)
                                        }
                                        "tl" -> {
                                            val nw = rectW - dx
                                            val nh = rectH - dy
                                            if (nw > 30f && nh > 30f && rectX + dx >= 0f && rectY + dy >= 0f) {
                                                rectX += dx; rectY += dy; rectW = nw; rectH = nh
                                            }
                                        }
                                        "tr" -> {
                                            val nw = rectW + dx
                                            val nh = rectH - dy
                                            if (nw > 30f && nh > 30f && rectX + nw <= canvasW && rectY + dy >= 0f) {
                                                rectY += dy; rectW = nw; rectH = nh
                                            }
                                        }
                                        "bl" -> {
                                            val nw = rectW - dx
                                            val nh = rectH + dy
                                            if (nw > 30f && nh > 30f && rectX + dx >= 0f && rectY + nh <= canvasH) {
                                                rectX += dx; rectW = nw; rectH = nh
                                            }
                                        }
                                        "br" -> {
                                            val nw = rectW + dx
                                            val nh = rectH + dy
                                            if (nw > 30f && nh > 30f && rectX + nw <= canvasW && rectY + nh <= canvasH) {
                                                rectW = nw; rectH = nh
                                            }
                                        }
                                    }
                                },
                                onDragEnd = { activeHandle = null }
                            )
                        }
                ) {
                    val dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt())
                    withTransform({
                        rotate(rotationDeg, pivot = Offset(size.width / 2f, size.height / 2f))
                    }) {
                        drawImage(image = imageBitmap, dstSize = dstSize)
                    }

                    // Dimmed mask outside cropRect
                    drawRect(Color.Black.copy(alpha = 0.5f), Offset.Zero, Size(size.width, rectY))
                    drawRect(
                        Color.Black.copy(alpha = 0.5f),
                        Offset(0f, rectY + rectH),
                        Size(size.width, size.height - (rectY + rectH))
                    )
                    drawRect(Color.Black.copy(alpha = 0.5f), Offset(0f, rectY), Size(rectX, rectH))
                    drawRect(
                        Color.Black.copy(alpha = 0.5f),
                        Offset(rectX + rectW, rectY),
                        Size(size.width - (rectX + rectW), rectH)
                    )

                    // White border + 4 corner handles (12x12)
                    drawRect(
                        color = Color.White,
                        topLeft = Offset(rectX, rectY),
                        size = Size(rectW, rectH),
                        style = Stroke(width = 4f)
                    )
                    val s = 24f
                    val corners = listOf(
                        Offset(rectX - s / 2f, rectY - s / 2f),
                        Offset(rectX + rectW - s / 2f, rectY - s / 2f),
                        Offset(rectX - s / 2f, rectY + rectH - s / 2f),
                        Offset(rectX + rectW - s / 2f, rectY + rectH - s / 2f)
                    )
                    corners.forEach { c ->
                        drawRect(color = Color.White, topLeft = c, size = Size(s, s))
                    }
                }
            }
        }

        // .crop-footer
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.Black)
                .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("↻", color = Color.White, fontSize = 20.sp)
                Slider(
                    value = rotationDeg,
                    onValueChange = { rotationDeg = it },
                    valueRange = 0f..360f,
                    colors = SliderDefaults.colors(
                        thumbColor = Color(0xFF229AF0),
                        activeTrackColor = Color(0xFF229AF0),
                        inactiveTrackColor = Color(0xFF333333)
                    ),
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "${rotationDeg.roundToInt()}°",
                    color = Color.White,
                    fontSize = 14.sp,
                    modifier = Modifier.width(48.dp)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = {
                        val orig = compressBitmapLikeJs(sourceBitmap, maxCompressedKb = 75)
                        onConfirmBytes(orig)
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1C1C1C)),
                    modifier = Modifier.weight(1f).height(48.dp)
                ) {
                    Text("ОРИГИНАЛ", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                }
                Button(
                    onClick = {
                        rotationDeg = 0f
                        rectX = 0f
                        rectY = 0f
                        rectW = canvasW
                        rectH = canvasH
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1C1C1C)),
                    modifier = Modifier.weight(1f).height(48.dp)
                ) {
                    Text("СБРОС", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

private fun performCropAndCompress(
    src: Bitmap,
    rotation: Float,
    canvasW: Float,
    canvasH: Float,
    rx: Float,
    ry: Float,
    rw: Float,
    rh: Float
): ByteArray {
    val rotated = if (rotation != 0f) {
        val m = Matrix().apply { postRotate(rotation) }
        Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true)
    } else src

    val cropX = ((rx / canvasW) * rotated.width).roundToInt().coerceIn(0, rotated.width - 1)
    val cropY = ((ry / canvasH) * rotated.height).roundToInt().coerceIn(0, rotated.height - 1)
    val cropW = ((rw / canvasW) * rotated.width).roundToInt().coerceIn(1, rotated.width - cropX)
    val cropH = ((rh / canvasH) * rotated.height).roundToInt().coerceIn(1, rotated.height - cropY)

    val cropped = Bitmap.createBitmap(rotated, cropX, cropY, cropW, cropH)
    return compressBitmapLikeJs(cropped, maxCompressedKb = 75)
}

// Exact port of compressImage in js/images.js (maxSide = 1920, maxCompressedSize = 75 KB)
fun compressBitmapLikeJs(bitmap: Bitmap, maxCompressedKb: Int = 75): ByteArray {
    var width = bitmap.width
    var height = bitmap.height
    val maxSide = 1920
    if (width > maxSide || height > maxSide) {
        if (width > height) {
            height = (height.toFloat() / width.toFloat() * maxSide).roundToInt()
            width = maxSide
        } else {
            width = (width.toFloat() / height.toFloat() * maxSide).roundToInt()
            height = maxSide
        }
    }
    val scaled = if (width != bitmap.width || height != bitmap.height) {
        Bitmap.createScaledBitmap(bitmap, width, height, true)
    } else bitmap

    var quality = 90
    var attempts = 0
    var outBytes: ByteArray
    do {
        val baos = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, quality, baos)
        outBytes = baos.toByteArray()
        if (outBytes.size / 1024 <= maxCompressedKb || attempts >= 10) break
        attempts++
        quality = (quality * 0.8f).roundToInt().coerceAtLeast(10)
    } while (true)

    return outBytes
}
