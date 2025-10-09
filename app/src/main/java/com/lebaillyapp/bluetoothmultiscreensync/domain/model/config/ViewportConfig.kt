package com.lebaillyapp.bluetoothmultiscreensync.domain.model.config

import androidx.compose.ui.geometry.Offset
import com.lebaillyapp.bluetoothmultiscreensync.domain.model.virtualPlane.VirtualOrientation

/**
 * Configuration d'un viewport pour un device.
 */
data class ViewportConfig(
    val deviceId: String,
    val offsetX: Float,
    val offsetY: Float,
    val width: Float,
    val height: Float,
    val screenWidthPx: Int,
    val screenHeightPx: Int,
    val orientation: VirtualOrientation = VirtualOrientation.NORMAL
) {
    val offset: Offset get() = Offset(offsetX, offsetY)
}