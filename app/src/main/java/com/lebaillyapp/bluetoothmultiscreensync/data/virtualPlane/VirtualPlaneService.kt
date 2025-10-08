package com.lebaillyapp.bluetoothmultiscreensync.data.virtualPlane

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import androidx.compose.ui.geometry.Offset
import com.lebaillyapp.bluetoothmultiscreensync.domain.model.virtualPlane.PlaneState
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

    /** Current state of the virtual plane system */
    private val _planeState = MutableStateFlow(PlaneState.IDLE)
    val planeState: StateFlow<PlaneState> = _planeState.asStateFlow()

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
        require(width > 0 && height > 0) { "Plane dimensions must be positive" }

        _planeWidth.value = width
        _planeHeight.value = height
        _planeState.value = PlaneState.READY
    }

    /**
     * Marks the plane as fully synced (called by Slaves after receiving Master data).
     */
    fun markAsSynced() {
        _planeState.value = PlaneState.SYNCED
    }

    /**
     * Defines or adds a viewport for a device.
     *
     * @param deviceId Device identifier
     * @param offsetX X position on the virtual plane
     * @param offsetY Y position on the virtual plane
     * @param width Width of the viewport in virtual units
     * @param height Height of the viewport in virtual units
     * @param screenWidthPx Physical screen width in pixels
     * @param screenHeightPx Physical screen height in pixels
     * @param orientation Virtual orientation applied (default NORMAL)
     */
    fun defineViewport(
        deviceId: String,
        offsetX: Float,
        offsetY: Float,
        width: Float,
        height: Float,
        screenWidthPx: Int,
        screenHeightPx: Int,
        orientation: VirtualOrientation = VirtualOrientation.NORMAL
    ) {
        // Validation
        require(width > 0 && height > 0) { "Viewport dimensions must be positive" }
        require(screenWidthPx > 0 && screenHeightPx > 0) { "Screen dimensions must be positive" }
        require(offsetX >= 0 && offsetY >= 0) { "Viewport offset must be >= 0" }

        // Check bounds only if plane is initialized
        if (_planeWidth.value > 0 && _planeHeight.value > 0) {
            require(offsetX + width <= _planeWidth.value) {
                "Viewport exceeds plane width: ${offsetX + width} > ${_planeWidth.value}"
            }
            require(offsetY + height <= _planeHeight.value) {
                "Viewport exceeds plane height: ${offsetY + height} > ${_planeHeight.value}"
            }
        }

        val vp = Viewport(
            deviceId, offsetX, offsetY, width, height,
            orientation, screenWidthPx, screenHeightPx
        )

        // Thread-safe update
        _viewports.update { currentMap ->
            currentMap + (deviceId to vp)
        }
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
     * @param screenWidthPx New physical screen width (optional)
     * @param screenHeightPx New physical screen height (optional)
     * @param orientation New virtual orientation (optional)
     */
    fun updateViewport(
        deviceId: String,
        offsetX: Float? = null,
        offsetY: Float? = null,
        width: Float? = null,
        height: Float? = null,
        screenWidthPx: Int? = null,
        screenHeightPx: Int? = null,
        orientation: VirtualOrientation? = null
    ) {
        _viewports.update { currentMap ->
            val current = currentMap[deviceId] ?: return@update currentMap

            val updated = current.copy(
                offsetX = offsetX ?: current.offsetX,
                offsetY = offsetY ?: current.offsetY,
                width = width ?: current.width,
                height = height ?: current.height,
                screenWidthPx = screenWidthPx ?: current.screenWidthPx,
                screenHeightPx = screenHeightPx ?: current.screenHeightPx,
                orientation = orientation ?: current.orientation
            )

            currentMap + (deviceId to updated)
        }
    }

    /**
     * Retrieves the viewport for a given device.
     *
     * @param deviceId Device identifier
     * @return Viewport associated or null if not defined
     */
    fun getViewport(deviceId: String): Viewport? = _viewports.value[deviceId]

    /**
     * Converts global virtual coordinates (VU) into local coordinates (LD)
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
     * Converts local device coordinates (LD) into global virtual coordinates (VU).
     *
     * This is the inverse transformation of virtualToLocal().
     * Used when a device detects touch input and needs to report it in virtual coordinates.
     *
     * @param deviceId Device identifier
     * @param localX X coordinate in local viewport space
     * @param localY Y coordinate in local viewport space
     * @return Offset corresponding to global virtual coordinates
     */
    fun localToVirtual(deviceId: String, localX: Float, localY: Float): Offset {
        val vp = _viewports.value[deviceId] ?: return Offset(localX, localY)

        // Inverse transformation according to orientation
        val (relativeX, relativeY) = when (vp.orientation) {
            VirtualOrientation.NORMAL -> localX to localY
            VirtualOrientation.ROTATED_90 -> localY to (vp.height - localX)
            VirtualOrientation.ROTATED_180 -> (vp.width - localX) to (vp.height - localY)
            VirtualOrientation.ROTATED_270 -> (vp.width - localY) to localX
            VirtualOrientation.FLIPPED_X -> (vp.width - localX) to localY
            VirtualOrientation.FLIPPED_Y -> localX to (vp.height - localY)
        }

        return Offset(
            x = relativeX + vp.offsetX,
            y = relativeY + vp.offsetY
        )
    }

    /**
     * Converts virtual coordinates (VU) into screen pixel coordinates (SC).
     *
     * This applies both the viewport transformation AND the pixel scaling.
     * Use this for actual rendering on screen.
     *
     * @param deviceId Device identifier
     * @param x Global X coordinate on the virtual plane
     * @param y Global Y coordinate on the virtual plane
     * @return Offset in screen pixel coordinates
     */
    fun virtualToScreen(deviceId: String, x: Float, y: Float): Offset {
        val vp = _viewports.value[deviceId] ?: return Offset.Zero
        val local = virtualToLocal(deviceId, x, y)

        return Offset(
            x = local.x * vp.scale,
            y = local.y * vp.scale
        )
    }

    /**
     * Converts screen pixel coordinates (SC) into virtual coordinates (VU).
     *
     * This is the inverse of virtualToScreen().
     * Use this to convert touch events from screen pixels to virtual coordinates.
     *
     * @param deviceId Device identifier
     * @param screenX X coordinate in screen pixels
     * @param screenY Y coordinate in screen pixels
     * @return Offset in virtual coordinates
     */
    fun screenToVirtual(deviceId: String, screenX: Float, screenY: Float): Offset {
        val vp = _viewports.value[deviceId] ?: return Offset(screenX, screenY)

        // First, unscale from screen pixels to local coordinates
        val localX = screenX / vp.scale
        val localY = screenY / vp.scale

        // Then convert from local to virtual
        return localToVirtual(deviceId, localX, localY)
    }

    /**
     * Removes a viewport for a given device.
     */
    fun removeViewport(deviceId: String) {
        _viewports.update { it - deviceId }
    }


    /**
     * Resets the virtual plane and all viewports.
     */
    fun clear() {
        _viewports.value = emptyMap()
        _planeWidth.value = 0f
        _planeHeight.value = 0f
        _planeState.value = PlaneState.IDLE
    }
}