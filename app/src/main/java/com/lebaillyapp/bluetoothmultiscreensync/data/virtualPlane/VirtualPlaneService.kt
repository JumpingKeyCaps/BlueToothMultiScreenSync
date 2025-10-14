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
 * Coordinate transformations now respect the **global scale factor (DP/VU)**
 * to ensure consistent physical size across devices.
 */
class VirtualPlaneService {

    // --- GLOBAL PLANE STATE ---

    /** Width of the global virtual plane in virtual units */
    private val _planeWidth = MutableStateFlow(0f)
    val planeWidth: StateFlow<Float> = _planeWidth.asStateFlow()

    /** Height of the global virtual plane in virtual units */
    private val _planeHeight = MutableStateFlow(0f)
    val planeHeight: StateFlow<Float> = _planeHeight.asStateFlow()

    /**
     * The global scale factor (Density-Independent Pixels per Virtual Unit).
     * This value is determined by the Master and is the contract for all devices,
     * ensuring consistent physical sizing regardless of DPI.
     * Modifying this value is how the Master implements global Zoom/Dezoom.
     */
    private val _scaleDpPerVu = MutableStateFlow(0f)
    val scaleDpPerVu: StateFlow<Float> = _scaleDpPerVu.asStateFlow()

    /** Current state of the virtual plane system */
    private val _planeState = MutableStateFlow(PlaneState.IDLE)
    val planeState: StateFlow<PlaneState> = _planeState.asStateFlow()

    /** Map of viewports by deviceId */
    private val _viewports = MutableStateFlow<Map<String, Viewport>>(emptyMap())
    val viewports: StateFlow<Map<String, Viewport>> = _viewports.asStateFlow()

    // --- INITIALIZATION AND CONFIGURATION ---

    /**
     * Initializes the global virtual plane size and sets the global scale factor.
     *
     * @param width Width of the plane in virtual units
     * @param height Height of the plane in virtual units
     * @param scaleDpPerVu The global scale factor (DP/VU) determined by the Master.
     */
    fun initPlane(width: Float, height: Float, scaleDpPerVu: Float) {
        require(width > 0 && height > 0) { "Plane dimensions must be positive" }
        require(scaleDpPerVu > 0) { "Scale factor must be positive" }

        _planeWidth.value = width
        _planeHeight.value = height
        _scaleDpPerVu.value = scaleDpPerVu
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
     * NOTE: This function now requires DP dimensions and density, which are part of the
     * corrected Viewport model.
     *
     * @param deviceId Device identifier
     * @param offsetX X position on the virtual plane (VU)
     * @param offsetY Y position on the virtual plane (VU)
     * @param width Width of the viewport in virtual units
     * @param height Height of the viewport in virtual units
     * @param screenWidthDp Screen width in Density-Independent Pixels (DP)
     * @param screenHeightDp Screen height in Density-Independent Pixels (DP)
     * @param density Device pixel density (Px/DP ratio)
     * @param orientation Virtual orientation applied (default NORMAL)
     */
    fun defineViewport(
        deviceId: String,
        offsetX: Float,
        offsetY: Float,
        width: Float,
        height: Float,
        screenWidthDp: Float,
        screenHeightDp: Float,
        density: Float,
        orientation: VirtualOrientation = VirtualOrientation.NORMAL
    ) {
        // Validation (using DP and Density from the new Viewport model)
        require(width > 0 && height > 0) { "Viewport dimensions must be positive" }
        require(screenWidthDp > 0 && screenHeightDp > 0) { "Screen DP dimensions must be positive" }
        require(density > 0) { "Density must be positive" }
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
            orientation, screenWidthDp, screenHeightDp, density
        )

        _viewports.update { currentMap ->
            currentMap + (deviceId to vp)
        }
    }

    /**
     * Updates an existing viewport partially.
     *
     * NOTE: Updated to reflect the new Viewport model parameters.
     *
     * @param deviceId Device identifier
     * @param offsetX New X position (optional)
     * @param offsetY New Y position (optional)
     * @param width New width (optional)
     * @param height New height (optional)
     * @param screenWidthDp New screen width in DP (optional)
     * @param screenHeightDp New screen height in DP (optional)
     * @param density New density (optional)
     * @param orientation New virtual orientation (optional)
     */
    fun updateViewport(
        deviceId: String,
        offsetX: Float? = null,
        offsetY: Float? = null,
        width: Float? = null,
        height: Float? = null,
        screenWidthDp: Float? = null,
        screenHeightDp: Float? = null,
        density: Float? = null,
        orientation: VirtualOrientation? = null
    ) {
        _viewports.update { currentMap ->
            val current = currentMap[deviceId] ?: return@update currentMap

            val updated = current.copy(
                offsetX = offsetX ?: current.offsetX,
                offsetY = offsetY ?: current.offsetY,
                width = width ?: current.width,
                height = height ?: current.height,
                screenWidthDp = screenWidthDp ?: current.screenWidthDp,
                screenHeightDp = screenHeightDp ?: current.screenHeightDp,
                density = density ?: current.density,
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

    // --- COORDINATE TRANSFORMATIONS (VU <-> Local) ---

    /**
     * Converts global virtual coordinates (VU) into local coordinates (LD)
     * within the device viewport, taking into account virtual orientation.
     *
     * @param deviceId Device identifier
     * @param x Global X coordinate on the virtual plane
     * @param y Global Y coordinate on the virtual plane
     * @return Offset corresponding to local coordinates for the viewport (still in VU)
     */
    fun virtualToLocal(deviceId: String, x: Float, y: Float): Offset {
        val vp = _viewports.value[deviceId] ?: return Offset(x, y)
        val relativeX = x - vp.offsetX
        val relativeY = y - vp.offsetY

        // Note: The logic below is correct as it operates on VU relative to the viewport.
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
     *
     * @param deviceId Device identifier
     * @param localX X coordinate in local viewport space (in VU)
     * @param localY Y coordinate in local viewport space (in VU)
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

    // --- COORDINATE TRANSFORMATIONS (VU <-> Screen) ---

    /**
     * Converts virtual coordinates (VU) into screen pixel coordinates (SC).
     *
     * This applies the viewport transformation, the global DP/VU scale, and the local Px/DP density.
     * This is the essential method for actual rendering on screen, guaranteeing physical size consistency.
     *
     * @param deviceId Device identifier
     * @param x Global X coordinate on the virtual plane
     * @param y Global Y coordinate on the virtual plane
     * @return Offset in screen pixel coordinates
     */
    fun virtualToScreen(deviceId: String, x: Float, y: Float): Offset {
        val vp = _viewports.value[deviceId] ?: return Offset.Zero
        val localVu = virtualToLocal(deviceId, x, y)
        val globalScale = _scaleDpPerVu.value

        // 1. VU -> DP (using global scale)
        val localDpX = localVu.x * globalScale
        val localDpY = localVu.y * globalScale

        // 2. DP -> Px (using local device density Px/DP)
        return Offset(
            x = localDpX * vp.density,
            y = localDpY * vp.density
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
        val globalScale = _scaleDpPerVu.value

        // 1. Px -> DP (using local device density DP/Px)
        val localDpX = screenX / vp.density
        val localDpY = screenY / vp.density

        // 2. DP -> VU (using inverse global scale)
        val localVuX = localDpX / globalScale
        val localVuY = localDpY / globalScale

        // Then convert from local to virtual
        return localToVirtual(deviceId, localVuX, localVuY)
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
        _scaleDpPerVu.value = 0f
        _planeState.value = PlaneState.IDLE
    }
}