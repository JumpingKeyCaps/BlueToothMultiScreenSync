package com.lebaillyapp.bluetoothmultiscreensync.domain.model.config

/**
 * Représente le VirtualPlane complet avec ses dimensions et les viewports associés.
 */
data class VirtualPlaneConfig(
    val planeWidth: Float,
    val planeHeight: Float,
    val viewports: List<ViewportConfig> = emptyList()
)