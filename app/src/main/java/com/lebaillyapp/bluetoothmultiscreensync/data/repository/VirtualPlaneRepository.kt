package com.lebaillyapp.bluetoothmultiscreensync.data.repository

import androidx.compose.ui.geometry.Offset
import com.lebaillyapp.bluetoothmultiscreensync.data.virtualPlane.VirtualPlaneService
import com.lebaillyapp.bluetoothmultiscreensync.domain.model.virtualPlane.Viewport
import com.lebaillyapp.bluetoothmultiscreensync.domain.model.virtualPlane.VirtualOrientation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Repository providing a clean API to access VirtualPlaneService
 * for ViewModels, without exposing internal mutable state.
 */
class VirtualPlaneRepository(
    private val service: VirtualPlaneService
) {

    /** Flow of the virtual plane width */
    val planeWidth: Flow<Float> = service.planeWidth

    /** Flow of the virtual plane height */
    val planeHeight: Flow<Float> = service.planeHeight

    /** Flow of all viewports */
    val viewports: Flow<Map<String, Viewport>> = service.viewports

    /** Initializes the virtual plane */
    fun initPlane(width: Float, height: Float) {
        service.initPlane(width, height)
    }

    /** Defines a viewport for a device */
    fun defineViewport(
        deviceId: String,
        offsetX: Float,
        offsetY: Float,
        width: Float,
        height: Float,
        orientation: VirtualOrientation = VirtualOrientation.NORMAL
    ) {
        service.defineViewport(deviceId, offsetX, offsetY, width, height, orientation)
    }

    /** Updates a viewport */
    fun updateViewport(
        deviceId: String,
        offsetX: Float? = null,
        offsetY: Float? = null,
        width: Float? = null,
        height: Float? = null,
        orientation: VirtualOrientation? = null
    ) {
        service.updateViewport(deviceId, offsetX, offsetY, width, height, orientation)
    }

    /** Retrieves a viewport */
    fun getViewport(deviceId: String): Viewport? = service.getViewport(deviceId)

    /** Converts virtual coordinates to local device coordinates */
    fun virtualToLocal(deviceId: String, x: Float, y: Float): Offset =
        service.virtualToLocal(deviceId, x, y)

    /** Clears plane and viewports */
    fun clear() = service.clear()

    /** Observes a single device viewport as Flow */
    fun observeViewport(deviceId: String): Flow<Viewport?> =
        service.viewports.map { it[deviceId] }
}