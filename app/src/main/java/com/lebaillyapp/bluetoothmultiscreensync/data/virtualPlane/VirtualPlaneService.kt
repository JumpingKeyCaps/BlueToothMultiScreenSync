package com.lebaillyapp.bluetoothmultiscreensync.data.virtualPlane

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import androidx.compose.ui.geometry.Offset
import com.lebaillyapp.bluetoothmultiscreensync.domain.model.virtualPlane.Viewport
import com.lebaillyapp.bluetoothmultiscreensync.domain.model.virtualPlane.VirtualOrientation


/**
 * Service responsible for managing the virtual plane and device viewports.
 *
 * Stores the global plane size, the viewports assigned to devices,
 * and exposes observable flows so ViewModels can react to changes.
 *
 * Coordinate transformations take into account the virtual orientation
 * defined by the master for each device.
 */
class VirtualPlaneService {

    /** Width of the global virtual plane in virtual units */
    private val _planeWidth = MutableStateFlow(0f)
    val planeWidth: StateFlow<Float> = _planeWidth.asStateFlow()

    /** Height of the global virtual plane in virtual units */
    private val _planeHeight = MutableStateFlow(0f)
    val planeHeight: StateFlow<Float> = _planeHeight.asStateFlow()

    /** Map of viewports by deviceId */
    private val _viewports = MutableStateFlow<Map<String, Viewport>>(emptyMap())
    val viewports: StateFlow<Map<String, Viewport>> = _viewports.asStateFlow()

    /**
     * Initializes the global virtual plane size.
     *
     * @param width Width of the plane in virtual units
     * @param height Height of the plane in virtual units
     */
    fun initPlane(width: Float, height: Float) {
        _planeWidth.value = width
        _planeHeight.value = height
    }

    /**
     * Defines or adds a viewport for a device.
     *
     * @param deviceId Device identifier
     * @param offsetX X position on the virtual plane
     * @param offsetY Y position on the virtual plane
     * @param width Width of the viewport
     * @param height Height of the viewport
     * @param orientation Virtual orientation applied (default NORMAL)
     */
    fun defineViewport(
        deviceId: String,
        offsetX: Float,
        offsetY: Float,
        width: Float,
        height: Float,
        orientation: VirtualOrientation = VirtualOrientation.NORMAL
    ) {
        val vp = Viewport(deviceId, offsetX, offsetY, width, height, orientation)
        _viewports.value = _viewports.value.toMutableMap().apply { put(deviceId, vp) }
    }

    /**
     * Updates an existing viewport partially.
     *
     * Allows updating only specific properties of the viewport
     * without overwriting the others.
     *
     * @param deviceId Device identifier
     * @param offsetX New X position (optional)
     * @param offsetY New Y position (optional)
     * @param width New width (optional)
     * @param height New height (optional)
     * @param orientation New virtual orientation (optional)
     */
    fun updateViewport(
        deviceId: String,
        offsetX: Float? = null,
        offsetY: Float? = null,
        width: Float? = null,
        height: Float? = null,
        orientation: VirtualOrientation? = null
    ) {
        val current = _viewports.value[deviceId] ?: return
        val updated = current.copy(
            offsetX = offsetX ?: current.offsetX,
            offsetY = offsetY ?: current.offsetY,
            width = width ?: current.width,
            height = height ?: current.height,
            orientation = orientation ?: current.orientation
        )
        _viewports.value = _viewports.value.toMutableMap().apply { put(deviceId, updated) }
    }

    /**
     * Retrieves the viewport for a given device.
     *
     * @param deviceId Device identifier
     * @return Viewport associated or null if not defined
     */
    fun getViewport(deviceId: String): Viewport? = _viewports.value[deviceId]

    /**
     * Converts global virtual coordinates (VU) into local coordinates
     * within the device viewport, taking into account virtual orientation.
     *
     * @param deviceId Device identifier
     * @param x Global X coordinate on the virtual plane
     * @param y Global Y coordinate on the virtual plane
     * @return Offset corresponding to local coordinates for the viewport
     */
    fun virtualToLocal(deviceId: String, x: Float, y: Float): Offset {
        val vp = _viewports.value[deviceId] ?: return Offset(x, y)
        val relativeX = x - vp.offsetX
        val relativeY = y - vp.offsetY

        return when (vp.orientation) {
            VirtualOrientation.NORMAL -> Offset(relativeX, relativeY)
            VirtualOrientation.ROTATED_90 -> Offset(vp.height - relativeY, relativeX)
            VirtualOrientation.ROTATED_180 -> Offset(vp.width - relativeX, vp.height - relativeY)
            VirtualOrientation.ROTATED_270 -> Offset(relativeY, vp.width - relativeX)
            VirtualOrientation.FLIPPED_X -> Offset(vp.width - relativeX, relativeY)
            VirtualOrientation.FLIPPED_Y -> Offset(relativeX, vp.height - relativeY)
        }
    }

    /**
     * Resets the virtual plane and all viewports.
     */
    fun clear() {
        _viewports.value = emptyMap()
        _planeWidth.value = 0f
        _planeHeight.value = 0f
    }
}