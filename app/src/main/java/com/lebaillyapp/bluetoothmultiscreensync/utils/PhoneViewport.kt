package com.lebaillyapp.bluetoothmultiscreensync.utils

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.max
import kotlin.math.roundToInt

@Composable
fun PhoneViewportStylish(
    id: String,
    isCurrentDevice: Boolean = false,
    isDragging: Boolean = false,
    isOverlapping: Boolean = false,
    isPortrait: Boolean,
    widthDp: Dp,
    heightDp: Dp,
    offsetX: Float,
    offsetY: Float,
    visualPaddingX: Float = 0f,
    visualPaddingY: Float = 0f,
    scale: Float = 1f,
    elevation: Float = 4f,
    onDrag: (dx: Float, dy: Float) -> Unit,
    onDragStart: () -> Unit,
    onDragEnd: () -> Unit,
    onRotate: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .offset {
                IntOffset(
                    ((offsetX + visualPaddingX).roundToInt()),
                    ((offsetY + visualPaddingY).roundToInt())
                )
            }
            .size(widthDp, heightDp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                shadowElevation = elevation
            }
            .pointerInput(id, isPortrait) {
                detectDragGestures(
                    onDragStart = { onDragStart() },
                    onDragEnd = { onDragEnd() },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        onDrag(dragAmount.x, dragAmount.y)
                    }
                )
            }
            .background(
                when {
                    isCurrentDevice -> Color(0xFF4B4456)
                    isDragging -> Color(0xFF6C6A6A)
                    else -> Color(0xFF232338)
                },
                shape = RoundedCornerShape(4.dp)
            )
            .then(
                if (isOverlapping) Modifier.border(1.dp, Color.Red, RoundedCornerShape(6.dp))
                else Modifier
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(id, color = Color.White, style = MaterialTheme.typography.bodySmall)
            if (isCurrentDevice) {
                Text("(You)", color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.labelSmall)
            }
            Spacer(modifier = Modifier.height(6.dp))
            IconButton(
                onClick = { onRotate() },
                modifier = Modifier.size(28.dp)
            ) {
                Icon(Icons.Default.Refresh, contentDescription = "Rotate", tint = Color.White)
            }
        }
    }
}