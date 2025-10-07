package com.lebaillyapp.bluetoothmultiscreensync.domain.model.virtualPlane

import kotlinx.serialization.Serializable
/**
 * Represents a single device viewport update.
 *
 * Can be sent individually to update offset, size, or orientation.
 */
@Serializable
data class ViewportPacket(
    val deviceId: String,
    val offsetX: Float,
    val offsetY: Float,
    val width: Float,
    val height: Float,
    val orientation: VirtualOrientation
)