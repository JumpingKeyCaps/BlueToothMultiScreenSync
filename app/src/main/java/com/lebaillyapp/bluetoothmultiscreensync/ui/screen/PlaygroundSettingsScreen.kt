package com.lebaillyapp.bluetoothmultiscreensync.ui.screen

import android.annotation.SuppressLint
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.consumeAllChanges
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.lebaillyapp.bluetoothmultiscreensync.domain.model.config.ViewportConfig
import com.lebaillyapp.bluetoothmultiscreensync.domain.model.config.VirtualPlaneConfig
import com.lebaillyapp.bluetoothmultiscreensync.domain.model.virtualPlane.VirtualOrientation
import com.lebaillyapp.bluetoothmultiscreensync.viewmodel.PlaygroundSettingsViewModel
import kotlin.collections.forEach
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

data class LocalViewport(
    val id: String,
    var offsetX: Float,
    var offsetY: Float,
    var isPortrait: Boolean = true,
    var isDragging: Boolean = false,
    var isOverlapping: Boolean = false,
    val isCurrentDevice: Boolean = false
) {
    fun bounds(widthPx: Float, heightPx: Float): Rect {
        return Rect(
            left = offsetX,
            top = offsetY,
            right = offsetX + widthPx,
            bottom = offsetY + heightPx
        )
    }
}

@SuppressLint("UnusedBoxWithConstraintsScope")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaygroundSettingsScreen(
    modifier: Modifier = Modifier,
    isMaster: Boolean = true,
    currentDeviceId: String = "Master",
    onValidate: (VirtualPlaneConfig) -> Unit = {}
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Playground Settings", style = MaterialTheme.typography.titleLarge)
                        Text(
                            if (isMaster) "Master Mode" else "Slave Mode",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                },
                actions = {
                    IconButton(onClick = {
                        // val config = generateVirtualPlaneConfig(viewports)
                        // onValidate(config)
                    }) {
                        Icon(Icons.Default.Check, contentDescription = "Validate")
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentAlignment = Alignment.Center
        ) {
            val planeWidthDp = 330.dp
            val planeHeightDp = 480.dp
            val density = LocalDensity.current

            val viewports = remember {
                mutableStateListOf(
                    LocalViewport("Master", 0f, 0f, isCurrentDevice = currentDeviceId == "Master"),
                    LocalViewport("Device A", 100f, 0f, isCurrentDevice = currentDeviceId == "Device A"),
                    LocalViewport("Device B", 200f, 0f, isCurrentDevice = currentDeviceId == "Device B")
                )
            }

            var planeWidthPx by remember { mutableStateOf(0f) }
            var planeHeightPx by remember { mutableStateOf(0f) }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(10.dp)
                    .height(planeHeightDp)
                    .onGloballyPositioned { coords ->
                        planeWidthPx = coords.size.width.toFloat()
                        planeHeightPx = coords.size.height.toFloat()
                    }
                    .background(Color(0xFF252531), shape = RoundedCornerShape(8.dp))
            ) {
                viewports.forEach { vp ->
                    val offsetX = remember(vp.id) { mutableStateOf(vp.offsetX) }
                    val offsetY = remember(vp.id) { mutableStateOf(vp.offsetY) }
                    var isPortrait by remember(vp.id) { mutableStateOf(vp.isPortrait) }
                    var isDragging by remember(vp.id) { mutableStateOf(false) }
                    var isOverlapping by remember(vp.id) { mutableStateOf(false) }

                    val widthDp = if (isPortrait) 80.dp else 160.dp
                    val heightDp = if (isPortrait) 160.dp else 80.dp

                    val viewportWidthPx = with(density) { widthDp.toPx() }
                    val viewportHeightPx = with(density) { heightDp.toPx() }

                    val maxX = max(0f, planeWidthPx - viewportWidthPx)
                    val maxY = max(0f, planeHeightPx - viewportHeightPx)

                    val clampedXPx = with(density) { offsetX.value.dp.toPx() }.coerceIn(0f, maxX)
                    val clampedYPx = with(density) { offsetY.value.dp.toPx() }.coerceIn(0f, maxY)

                    val scale by animateFloatAsState(if (isDragging) 1.0f else 1f)
                    val elevation by animateFloatAsState(if (isDragging) 12f else 4f)

                    // Couleur du viewport
                    val backgroundColor = when {
                        vp.isCurrentDevice -> Color(0xFF4CAF50)
                        isDragging -> Color(0xFF2979FF)
                        else -> Color(0xFF546E7A)
                    }

                    // Détection de superposition
                    fun checkOverlap(): Boolean {
                        val currentWidthPx = with(density) { (if (isPortrait) 80.dp else 160.dp).toPx() }
                        val currentHeightPx = with(density) { (if (isPortrait) 160.dp else 80.dp).toPx() }

                        val currentRect = Rect(
                            offsetX.value * density.density,
                            offsetY.value * density.density,
                            offsetX.value * density.density + currentWidthPx,
                            offsetY.value * density.density + currentHeightPx
                        )

                        return viewports.any { other ->
                            if (other == vp) return@any false

                            val otherWidthPx = with(density) { (if (other.isPortrait) 80.dp else 160.dp).toPx() }
                            val otherHeightPx = with(density) { (if (other.isPortrait) 160.dp else 80.dp).toPx() }

                            val otherRect = Rect(
                                other.offsetX * density.density,
                                other.offsetY * density.density,
                                other.offsetX * density.density + otherWidthPx,
                                other.offsetY * density.density + otherHeightPx
                            )

                            currentRect.overlaps(otherRect)
                        }
                    }

                    Box(
                        modifier = Modifier
                            .offset { IntOffset(clampedXPx.roundToInt(), clampedYPx.roundToInt()) }
                            .size(widthDp, heightDp)
                            .graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                                shadowElevation = elevation
                            }
                            .pointerInput(vp.id, planeWidthPx, planeHeightPx, isPortrait) {
                                val densityValue = density.density
                                detectDragGestures(
                                    onDragStart = {
                                        isDragging = true
                                        vp.isDragging = true
                                    },
                                    onDragEnd = {
                                        isDragging = false
                                        vp.isDragging = false
                                        vp.offsetX = offsetX.value
                                        vp.offsetY = offsetY.value
                                        isOverlapping = checkOverlap()
                                        vp.isOverlapping = isOverlapping
                                    }
                                ) { change, dragAmount ->
                                    change.consume()
                                    val dragXdp = dragAmount.x / densityValue
                                    val dragYdp = dragAmount.y / densityValue
                                    val maxXdp = (maxX / densityValue)
                                    val maxYdp = (maxY / densityValue)

                                    val newX = (offsetX.value + dragXdp).coerceIn(0f, maxXdp)
                                    val newY = (offsetY.value + dragYdp).coerceIn(0f, maxYdp)

                                    offsetX.value = newX
                                    offsetY.value = newY
                                    vp.offsetX = newX
                                    vp.offsetY = newY

                                    // Vérifie en live le chevauchement
                                    isOverlapping = checkOverlap()
                                    vp.isOverlapping = isOverlapping
                                }
                            }
                            .background(backgroundColor, shape = RoundedCornerShape(6.dp))
                            .then(
                                if (isOverlapping)
                                    Modifier.border(2.dp, Color.Red, RoundedCornerShape(6.dp))
                                else Modifier
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(vp.id, color = Color.White, style = MaterialTheme.typography.bodySmall)
                            if (vp.isCurrentDevice) {
                                Text(
                                    "(You)",
                                    color = Color.White.copy(alpha = 0.7f),
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            IconButton(
                                onClick = {
                                    isPortrait = !isPortrait
                                    vp.isPortrait = isPortrait
                                    isOverlapping = checkOverlap()
                                    vp.isOverlapping = isOverlapping
                                },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = "Rotate", tint = Color.White)
                            }
                        }
                    }
                }

                Text(
                    "Drag viewports to snap them together",
                    color = Color.White.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp)
                )
            }
        }
    }
}

// Génération config finale
private fun generateVirtualPlaneConfig(viewports: List<LocalViewport>): VirtualPlaneConfig {
    if (viewports.isEmpty()) return VirtualPlaneConfig(planeWidth = 0f, planeHeight = 0f)

    val viewportConfigs = viewports.map { vp ->
        ViewportConfig(
            deviceId = vp.id,
            offsetX = vp.offsetX,
            offsetY = vp.offsetY,
            width = if (vp.isPortrait) 100f else 180f,
            height = if (vp.isPortrait) 180f else 100f,
            screenWidthPx = 1080,
            screenHeightPx = 2400,
            orientation = VirtualOrientation.NORMAL
        )
    }

    val minX = viewportConfigs.minOf { it.offsetX }
    val minY = viewportConfigs.minOf { it.offsetY }
    val maxX = viewportConfigs.maxOf { it.offsetX + it.width }
    val maxY = viewportConfigs.maxOf { it.offsetY + it.height }

    return VirtualPlaneConfig(
        planeWidth = maxX - minX,
        planeHeight = maxY - minY,
        viewports = viewportConfigs
    )
}






