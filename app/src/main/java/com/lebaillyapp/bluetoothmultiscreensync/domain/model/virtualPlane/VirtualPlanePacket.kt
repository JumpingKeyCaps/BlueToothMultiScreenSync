package com.lebaillyapp.bluetoothmultiscreensync.domain.model.virtualPlane

import kotlinx.serialization.Serializable
/**
 * Represents the global virtual plane, sent once at initialization.
 *
 * Typically broadcast from Master → Slaves at startup.
 */
@Serializable
data class VirtualPlanePacket(
    val width: Float,
    val height: Float
)