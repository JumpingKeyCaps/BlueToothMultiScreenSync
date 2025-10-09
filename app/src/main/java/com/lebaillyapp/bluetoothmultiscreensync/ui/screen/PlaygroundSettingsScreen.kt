package com.lebaillyapp.bluetoothmultiscreensync.ui.screen

import android.annotation.SuppressLint
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.consumeAllChanges
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.lebaillyapp.bluetoothmultiscreensync.viewmodel.PlaygroundSettingsViewModel
import kotlin.collections.forEach
import kotlin.math.max
import kotlin.math.roundToInt

data class LocalViewport(
    val id: String,
    var offsetX: Float,
    var offsetY: Float,
    var isPortrait: Boolean = true
)

@SuppressLint("UnusedBoxWithConstraintsScope")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaygroundSettingsScreen(
    viewModel: PlaygroundSettingsViewModel,
    isMaster: Boolean = true,
    onValidate: () -> Unit = {}
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Playground Settings", style = MaterialTheme.typography.titleLarge)
                        Text(if (isMaster) "Master Mode" else "Slave Mode", style = MaterialTheme.typography.bodyMedium)
                    }
                },
                actions = {
                    IconButton(onClick = onValidate) {
                        Icon(Icons.Default.Check, contentDescription = "Validate")
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentAlignment = Alignment.Center
        ) {
            // fixed preview plane size in DP for simplicity (you can adapt to BoxWithConstraints later)
            val planeWidthDp = 330.dp
            val planeHeightDp = 480.dp

            // fakelist of viewports
            val viewports = remember {
                mutableStateListOf(
                    LocalViewport("Master", 0f, 0f),
                    LocalViewport("Device A", 140f, 0f),
                    LocalViewport("Device B", 340f, 0f)
                )
            }

            val density = LocalDensity.current

            // measure plane pixels
            var planeWidthPx by remember { mutableStateOf(0f) }
            var planeHeightPx by remember { mutableStateOf(0f) }

            Box(
                modifier = Modifier
                    .size(planeWidthDp, planeHeightDp)
                    .onGloballyPositioned { coords ->
                        planeWidthPx = coords.size.width.toFloat()
                        planeHeightPx = coords.size.height.toFloat()
                    }
                    .background(Color(0xFF252531), shape = RoundedCornerShape(8.dp))
            ) {
                // For each viewport create an independent composable with its local states
                viewports.forEach { vp ->
                    // local mutable states per viewport (stable across recompositions)
                    var offsetX by remember(vp.id) { mutableStateOf(vp.offsetX) }
                    var offsetY by remember(vp.id) { mutableStateOf(vp.offsetY) }
                    var isPortrait by remember(vp.id) { mutableStateOf(vp.isPortrait) }
                    var isDragging by remember(vp.id) { mutableStateOf(false) }

                    // choose sizes in dp (visual)
                    val widthDp = if (isPortrait) 100.dp else 180.dp
                    val heightDp = if (isPortrait) 180.dp else 100.dp

                    // convert to px for clamp
                    val viewportWidthPx = with(density) { widthDp.toPx() }
                    val viewportHeightPx = with(density) { heightDp.toPx() }

                    // ensure max values non-negative to avoid coerceIn empty range
                    val maxX = max(0f, planeWidthPx - viewportWidthPx)
                    val maxY = max(0f, planeHeightPx - viewportHeightPx)

                    // keep offsets clamped after rotations / plane resize
                    LaunchedEffect(isPortrait, planeWidthPx, planeHeightPx) {
                        offsetX = offsetX.coerceIn(0f, maxX)
                        offsetY = offsetY.coerceIn(0f, maxY)
                        vp.offsetX = offsetX
                        vp.offsetY = offsetY
                    }

                    val scale by animateFloatAsState(if (isDragging) 1.0f else 1f)
                    val elevation by animateFloatAsState(if (isDragging) 12f else 4f)


                    Box(
                        modifier = Modifier
                            .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
                            .size(widthDp, heightDp)
                            .graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                                shadowElevation = elevation

                            }
                            .pointerInput(vp.id, planeWidthPx, planeHeightPx, isPortrait) {
                                detectDragGestures(
                                    onDragStart = { isDragging = true },
                                    onDragEnd = {
                                        isDragging = false
                                        // persist final pos
                                        vp.offsetX = offsetX
                                        vp.offsetY = offsetY
                                    },
                                    onDragCancel = {
                                        isDragging = false
                                    }
                                ) { change, dragAmount ->
                                    change.consumeAllChanges()
                                    // if plane not measured yet skip
                                    if (planeWidthPx <= 0f || planeHeightPx <= 0f) return@detectDragGestures

                                    // compute new offsets in px and clamp using maxX/maxY above
                                    val newX = (offsetX + dragAmount.x).coerceIn(0f, maxX)
                                    val newY = (offsetY + dragAmount.y).coerceIn(0f, maxY)

                                    offsetX = newX
                                    offsetY = newY

                                    // immediate update to the backing model list
                                    vp.offsetX = offsetX
                                    vp.offsetY = offsetY
                                }
                            }
                            .background(Color(0xFF2979FF), shape = RoundedCornerShape(6.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(vp.id, color = Color.White)
                            Spacer(modifier = Modifier.height(6.dp))
                            IconButton(
                                onClick = {
                                    // flip orientation and clamp to new sizes
                                    isPortrait = !isPortrait
                                    vp.isPortrait = isPortrait
                                    // clamp done by LaunchedEffect
                                },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = "Rotate", tint = Color.White)
                            }
                        }
                    }
                }
            }
        }
    }
}







