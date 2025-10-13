package com.lebaillyapp.bluetoothmultiscreensync.ui.screen

import android.annotation.SuppressLint
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.lebaillyapp.bluetoothmultiscreensync.R
import com.lebaillyapp.bluetoothmultiscreensync.utils.PhoneViewportStylish
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Data class representing a local viewport on the virtual plane.
 *
 * @param id Unique identifier of the viewport
 * @param offsetX Horizontal offset in dp
 * @param offsetY Vertical offset in dp
 * @param isPortrait Whether the viewport is in portrait orientation
 * @param isDragging Whether the viewport is currently being dragged
 * @param isOverlapping Whether the viewport is overlapping another viewport
 * @param isCurrentDevice Whether this viewport corresponds to the current device
 */
data class LocalViewport(
    val id: String,
    var offsetX: Float,
    var offsetY: Float,
    var isPortrait: Boolean = true,
    var isDragging: Boolean = false,
    var isOverlapping: Boolean = false,
    val isCurrentDevice: Boolean = false
)

/**
 * Playground Settings Screen.
 *
 * Displays a virtual plane with draggable viewports, allowing the user
 * to reposition devices and auto-align them with smooth animations.
 *
 * @param modifier Modifier applied to the root Box
 * @param isMaster Whether this device is in Master mode
 * @param currentDeviceId ID of the current device
 * @param onValidate Callback invoked when user validates configuration
 */
@SuppressLint("UnusedBoxWithConstraintsScope")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaygroundSettingsScreen(
    modifier: Modifier = Modifier,
    isMaster: Boolean = true,
    currentDeviceId: String = "Master",
    onValidate: (Any) -> Unit = {}
) {
    // Stores mutable state for viewport X/Y positions
    val vpOffsetXStates = remember { mutableMapOf<String, MutableState<Float>>() }
    val vpOffsetYStates = remember { mutableMapOf<String, MutableState<Float>>() }

    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // Visual padding applied to viewports for spacing during alignment
    val visualPaddingX = remember { mutableStateMapOf<String, Float>() }
    val visualPaddingY = remember { mutableStateMapOf<String, Float>() }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
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
                        // Placeholder for validation action
                        // onValidate(generateVirtualPlaneConfig(viewports))
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

            // Initial list of viewports
            val viewports = remember {
                mutableStateListOf(
                    LocalViewport("Master", 0f, 0f, isCurrentDevice = currentDeviceId == "Master"),
                    LocalViewport("Device A", 100f, 0f, isCurrentDevice = currentDeviceId == "Device A"),
                    LocalViewport("Device B", 200f, 0f, isCurrentDevice = currentDeviceId == "Device B")
                )
            }

            var planeWidthPx by remember { mutableStateOf(0f) }
            var planeHeightPx by remember { mutableStateOf(0f) }

            /**
             * Rechecks all viewports for overlaps.
             *
             * Updates each viewport's `isOverlapping` flag based on other viewports.
             */
            fun recheckAllOverlaps() {
                viewports.forEach { vp ->
                    val vpWidth = if (vp.isPortrait) 80f else 160f
                    val vpHeight = if (vp.isPortrait) 160f else 80f

                    val currentRect = Rect(
                        vp.offsetX * density.density,
                        vp.offsetY * density.density,
                        vp.offsetX * density.density + vpWidth * density.density,
                        vp.offsetY * density.density + vpHeight * density.density
                    )

                    vp.isOverlapping = viewports.any { other ->
                        if (other == vp) return@any false
                        val otherW = if (other.isPortrait) 80f else 160f
                        val otherH = if (other.isPortrait) 160f else 80f

                        val otherRect = Rect(
                            other.offsetX * density.density,
                            other.offsetY * density.density,
                            other.offsetX * density.density + otherW * density.density,
                            other.offsetY * density.density + otherH * density.density
                        )

                        currentRect.overlaps(otherRect)
                    }
                }
            }

            // Virtual plane container
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(10.dp)
                    .fillMaxHeight(0.9f)
                    .onGloballyPositioned { coords ->
                        planeWidthPx = coords.size.width.toFloat()
                        planeHeightPx = coords.size.height.toFloat()
                    }
                    .background(MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(8.dp))
            ) {

                // Render each viewport
                viewports.forEach { vp ->
                    val offsetX = remember(vp.id) { mutableStateOf(vp.offsetX) }
                    val offsetY = remember(vp.id) { mutableStateOf(vp.offsetY) }

                    vpOffsetXStates[vp.id] = offsetX
                    vpOffsetYStates[vp.id] = offsetY

                    var isPortrait by remember(vp.id) { mutableStateOf(vp.isPortrait) }
                    var isDragging by remember(vp.id) { mutableStateOf(false) }
                    var isOverlapping by remember(vp.id) { mutableStateOf(false) }

                    val widthDp = if (isPortrait) 80.dp else 160.dp
                    val heightDp = if (isPortrait) 160.dp else 80.dp

                    val viewportWidthPx = with(density) { widthDp.toPx() }
                    val viewportHeightPx = with(density) { heightDp.toPx() }

                    val maxX = max(0f, planeWidthPx - viewportWidthPx)
                    val maxY = max(0f, planeHeightPx - viewportHeightPx)

                    val clampedXPx = (offsetX.value * density.density).coerceIn(0f, maxX)
                    val clampedYPx = (offsetY.value * density.density).coerceIn(0f, maxY)

                    val scale by animateFloatAsState(if (isDragging) 1.0f else 1f)
                    val elevation by animateFloatAsState(if (isDragging) 12f else 4f)

                    PhoneViewportStylish(
                        id = vp.id,
                        isCurrentDevice = vp.isCurrentDevice,
                        isDragging = isDragging,
                        isOverlapping = isOverlapping,
                        isPortrait = isPortrait,
                        widthDp = widthDp,
                        heightDp = heightDp,
                        offsetX = clampedXPx + (visualPaddingX[vp.id] ?: 0f) * density.density,
                        offsetY = clampedYPx + (visualPaddingY[vp.id] ?: 0f) * density.density,
                        visualPaddingX = visualPaddingX[vp.id] ?: 0f,
                        visualPaddingY = visualPaddingY[vp.id] ?: 0f,
                        scale = scale,
                        elevation = elevation,
                        onDragStart = {
                            isDragging = true
                            vp.isDragging = true
                            visualPaddingX[vp.id] = 0f
                            visualPaddingY[vp.id] = 0f
                        },
                        onDragEnd = {
                            isDragging = false
                            vp.isDragging = false
                            vp.offsetX = offsetX.value
                            vp.offsetY = offsetY.value
                            recheckAllOverlaps()
                            isOverlapping = vp.isOverlapping
                        },
                        onDrag = { dx, dy ->
                            val dragXdp = dx / density.density
                            val dragYdp = dy / density.density
                            val maxXdp = maxX / density.density
                            val maxYdp = maxY / density.density

                            val newX = (offsetX.value + dragXdp).coerceIn(0f, maxXdp)
                            val newY = (offsetY.value + dragYdp).coerceIn(0f, maxYdp)

                            offsetX.value = newX
                            offsetY.value = newY
                            vp.offsetX = newX
                            vp.offsetY = newY

                            recheckAllOverlaps()
                            isOverlapping = vp.isOverlapping
                        },
                        onRotate = {
                            isPortrait = !isPortrait
                            vp.isPortrait = isPortrait
                            recheckAllOverlaps()
                            isOverlapping = vp.isOverlapping
                        },
                        modifier = Modifier
                    )
                }


                // Instructions
                Text(
                    "Drag on position the viewports,\n then press the button to auto-align them.",
                    color = Color.White.copy(alpha = 0.7f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp)
                )
            }

            /**
             * Floating Action Button to auto-align all viewports.
             *
             * Performs vertical, horizontal, and center alignment with
             * smooth elastic animation and collision padding.
             */
            FloatingActionButton(
                onClick = {
                    val hasOverlap = viewports.any { it.isOverlapping }

                    if (hasOverlap) {
                        scope.launch {
                            snackbarHostState.showSnackbar("Cannot align: viewports are overlapping!")
                        }
                        return@FloatingActionButton
                    }

                    val oldPositions = viewports.associate { it.id to (it.offsetX to it.offsetY) }

                    // Perform alignment logic
                    autoAlignVertical(viewports)
                    autoAlignHorizontal(viewports)
                    centerViewports(
                        viewports,
                        planeWidthPx / density.density,
                        planeHeightPx / density.density
                    )

                    // Animate each viewport
                    scope.launch {
                        val paddingDp = 4f

                        viewports.forEach { vp ->
                            val (oldX, oldY) = oldPositions[vp.id] ?: return@forEach

                            // Compute target padding based on nearby viewports
                            val vpAbove = viewports.count { other ->
                                other != vp &&
                                        other.offsetY + (if (other.isPortrait) 160f else 80f) <= vp.offsetY &&
                                        vp.offsetX < other.offsetX + (if (other.isPortrait) 80f else 160f) &&
                                        vp.offsetX + (if (vp.isPortrait) 80f else 160f) > other.offsetX
                            }
                            val vpLeft = viewports.count { other ->
                                other != vp &&
                                        other.offsetX + (if (other.isPortrait) 80f else 160f) <= vp.offsetX &&
                                        vp.offsetY < other.offsetY + (if (other.isPortrait) 160f else 80f) &&
                                        vp.offsetY + (if (vp.isPortrait) 160f else 80f) > other.offsetY
                            }

                            val targetPadX = vpLeft * paddingDp
                            val targetPadY = vpAbove * paddingDp
                            val oldPadX = visualPaddingX[vp.id] ?: 0f
                            val oldPadY = visualPaddingY[vp.id] ?: 0f

                            val animX = Animatable(oldX)
                            val animY = Animatable(oldY)
                            val animPadX = Animatable(oldPadX)
                            val animPadY = Animatable(oldPadY)

                            launch {
                                val duration = 1500
                                // Animate X/Y and padding using EaseOutElastic
                                launch {
                                    animX.animateTo(
                                        targetValue = vp.offsetX,
                                        animationSpec = tween(duration, easing = androidx.compose.animation.core.EaseOutElastic)
                                    )
                                }
                                launch {
                                    animY.animateTo(
                                        targetValue = vp.offsetY,
                                        animationSpec = tween(duration, easing = androidx.compose.animation.core.EaseOutElastic)
                                    )
                                }
                                launch {
                                    animPadX.animateTo(
                                        targetValue = targetPadX,
                                        animationSpec = tween(duration, easing = androidx.compose.animation.core.EaseOutElastic)
                                    )
                                }
                                launch {
                                    animPadY.animateTo(
                                        targetValue = targetPadY,
                                        animationSpec = tween(duration, easing = androidx.compose.animation.core.EaseOutElastic)
                                    )
                                }

                                // Update viewport states per frame (~60fps)
                                repeat(duration / 16) {
                                    vpOffsetXStates[vp.id]?.value = animX.value + animPadX.value
                                    vpOffsetYStates[vp.id]?.value = animY.value + animPadY.value
                                    visualPaddingX[vp.id] = animPadX.value
                                    visualPaddingY[vp.id] = animPadY.value
                                    kotlinx.coroutines.delay(16)
                                }

                                // Final snap
                                vpOffsetXStates[vp.id]?.value = vp.offsetX + targetPadX
                                vpOffsetYStates[vp.id]?.value = vp.offsetY + targetPadY
                                visualPaddingX[vp.id] = targetPadX
                                visualPaddingY[vp.id] = targetPadY
                            }
                        }
                    }
                },
                containerColor = Color(0xFF25252F),
                contentColor = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(24.dp)
                    .size(64.dp)
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.viewports_ico),
                    contentDescription = "Auto-align viewports",
                    modifier = Modifier.size(32.dp)
                )
            }
        }
    }
}

/**
 * Auto-aligns viewports vertically.
 *
 * Each viewport is positioned below the highest overlapping viewport above it.
 */
fun autoAlignVertical(viewports: List<LocalViewport>) {
    val sorted = viewports.sortedBy { it.offsetY }

    for (current in sorted) {
        val curW = if (current.isPortrait) 80f else 160f

        val candidates = sorted.filter { other ->
            if (other == current) return@filter false
            val otherW = if (other.isPortrait) 80f else 160f
            val otherH = if (other.isPortrait) 160f else 80f

            val horizontallyOverlap = current.offsetX < other.offsetX + otherW &&
                    current.offsetX + curW > other.offsetX

            horizontallyOverlap && (other.offsetY + otherH <= current.offsetY)
        }

        current.offsetY = if (candidates.isNotEmpty()) {
            candidates.maxOf { it.offsetY + if (it.isPortrait) 160f else 80f }
        } else 0f
    }
}

/**
 * Auto-aligns viewports horizontally.
 *
 * Each viewport is positioned to the right of the farthest overlapping viewport to its left.
 */
fun autoAlignHorizontal(viewports: List<LocalViewport>) {
    val sorted = viewports.sortedBy { it.offsetX }

    for (current in sorted) {
        val curH = if (current.isPortrait) 160f else 80f

        val candidates = sorted.filter { other ->
            if (other == current) return@filter false
            val otherW = if (other.isPortrait) 80f else 160f
            val otherH = if (other.isPortrait) 160f else 80f

            val verticallyOverlap = current.offsetY < other.offsetY + otherH &&
                    current.offsetY + curH > other.offsetY

            verticallyOverlap && (other.offsetX + otherW <= current.offsetX)
        }

        current.offsetX = if (candidates.isNotEmpty()) {
            candidates.maxOf { it.offsetX + if (it.isPortrait) 80f else 160f }
        } else 0f
    }
}

/**
 * Centers the group of viewports within the virtual plane.
 */
fun centerViewports(viewports: List<LocalViewport>, planeWidth: Float, planeHeight: Float) {
    if (viewports.isEmpty()) return

    val minX = viewports.minOf { it.offsetX }
    val maxX = viewports.maxOf { it.offsetX + if (it.isPortrait) 80f else 160f }
    val minY = viewports.minOf { it.offsetY }
    val maxY = viewports.maxOf { it.offsetY + if (it.isPortrait) 160f else 80f }

    val groupWidth = maxX - minX
    val groupHeight = maxY - minY

    val centerOffsetX = (planeWidth - groupWidth) / 2f - minX
    val centerOffsetY = (planeHeight - groupHeight) / 2f - minY

    viewports.forEach { vp ->
        vp.offsetX += centerOffsetX
        vp.offsetY += centerOffsetY
    }
}
