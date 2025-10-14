package com.lebaillyapp.bluetoothmultiscreensync.data.repository

import androidx.compose.ui.geometry.Offset
import com.lebaillyapp.bluetoothmultiscreensync.data.virtualPlane.VirtualPlaneService
import com.lebaillyapp.bluetoothmultiscreensync.domain.model.virtualPlane.Viewport
import com.lebaillyapp.bluetoothmultiscreensync.domain.model.virtualPlane.VirtualOrientation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * # VirtualPlaneRepository
 * Repository providing a clean, reactive API for managing the virtual plane coordinate system,
 * based on the Density-Independent Pixel (DP) contract for physical size consistency.
 *
 * This repository acts as an abstraction layer between ViewModels and the [VirtualPlaneService].
 *
 * ... [Documentation conservée pour les responsabilités et la thread safety] ...
 *
 * @property service The underlying [VirtualPlaneService] that manages state
 *
 * @see VirtualPlaneService The service layer managing viewport state
 * @see Viewport Domain model representing a device's viewport configuration
 */
class VirtualPlaneRepository(
    private val service: VirtualPlaneService
) {

    // --- REACTIVE STREAMS ---

    /** Reactive stream of the virtual plane's width in virtual units. */
    val planeWidth: Flow<Float> = service.planeWidth

    /** Reactive stream of the virtual plane's height in virtual units. */
    val planeHeight: Flow<Float> = service.planeHeight

    /**
     * Reactive stream of the **Global Scale Factor** (Density-Independent Pixels per Virtual Unit).
     * This factor guarantees consistent physical sizing across all connected devices.
     */
    val scaleDpPerVu: Flow<Float> = service.scaleDpPerVu

    /** Reactive stream of all device viewports currently configured. */
    val viewports: Flow<Map<String, Viewport>> = service.viewports

    // --- INITIALIZATION AND MANAGEMENT ---

    /**
     * Initializes the global virtual plane dimensions and the global scaling contract.
     *
     * This must be called **once** by the master device.
     *
     * @param width Total width of the virtual plane (VU)
     * @param height Total height of the virtual plane (VU)
     * @param scaleDpPerVu The mandated global scale factor (DP/VU) for all devices.
     * @throws IllegalArgumentException if dimensions or scale are invalid
     *
     * @see defineViewport To assign portions of this plane to devices
     */
    fun initPlane(width: Float, height: Float, scaleDpPerVu: Float) {
        service.initPlane(width, height, scaleDpPerVu)
    }

    /**
     * Defines a new viewport configuration for a specific device.
     *
     * **NOTE**: This now requires DP dimensions and density for local scale calculation.
     *
     * @param deviceId Unique identifier for the device
     * @param offsetX X position of the viewport's top-left corner (VU)
     * @param offsetY Y position of the viewport's top-left corner (VU)
     * @param width Width of the viewport (VU)
     * @param height Height of the viewport (VU)
     * @param screenWidthDp Physical screen width in **Density-Independent Pixels (DP)**
     * @param screenHeightDp Physical screen height in **Density-Independent Pixels (DP)**
     * @param density Device pixel density (Px/DP ratio)
     * @param orientation Virtual orientation applied (default: NORMAL)
     *
     * @throws IllegalArgumentException if dimensions are invalid or viewport exceeds plane bounds
     */
    fun defineViewport(
        deviceId: String,
        offsetX: Float,
        offsetY: Float,
        width: Float,
        height: Float,
        screenWidthDp: Float, // CORRECTION
        screenHeightDp: Float, // CORRECTION
        density: Float, // CORRECTION
        orientation: VirtualOrientation = VirtualOrientation.NORMAL
    ) {
        service.defineViewport(
            deviceId, offsetX, offsetY, width, height,
            screenWidthDp, screenHeightDp, density, orientation
        )
    }

    /**
     * Partially updates an existing viewport's configuration.
     *
     * **NOTE**: Updated to allow optional modification of DP dimensions and density.
     *
     * @param deviceId Unique identifier of the viewport to update
     * @param offsetX New X offset (optional)
     * @param offsetY New Y offset (optional)
     * @param width New width in VU (optional)
     * @param height New height in VU (optional)
     * @param screenWidthDp New screen width in DP (optional)
     * @param screenHeightDp New screen height in DP (optional)
     * @param density New device density (optional)
     * @param orientation New virtual orientation (optional)
     *
     * @see defineViewport To create a new viewport
     */
    fun updateViewport(
        deviceId: String,
        offsetX: Float? = null,
        offsetY: Float? = null,
        width: Float? = null,
        height: Float? = null,
        screenWidthDp: Float? = null, // CORRECTION
        screenHeightDp: Float? = null, // CORRECTION
        density: Float? = null, // CORRECTION
        orientation: VirtualOrientation? = null
    ) {
        service.updateViewport(
            deviceId, offsetX, offsetY, width, height,
            screenWidthDp, screenHeightDp, density, orientation
        )
    }

    /**
     * Retrieves the viewport configuration for a specific device.
     */
    fun getViewport(deviceId: String): Viewport? = service.getViewport(deviceId)

    // --- COORDINATE TRANSFORMATIONS ---

    /**
     * Converts global virtual coordinates to local device coordinates.
     *
     * **Note**: Output is still in **Virtual Units (VU)**.
     *
     * @param deviceId Device identifier whose viewport defines the transformation
     * @param x Global X coordinate on the virtual plane
     * @param y Global Y coordinate on the virtual plane
     * @return [Offset] in local device coordinates (still in virtual units)
     *
     */
    fun virtualToLocal(deviceId: String, x: Float, y: Float): Offset =
        service.virtualToLocal(deviceId, x, y)


    /**
     * Converts virtual coordinates (VU) into screen pixel coordinates (SC).
     *
     * This transformation ensures physical size consistency using the global DP/VU scale
     * and the device's local density (Px/DP). Use this for actual rendering.
     *
     * @param deviceId Device identifier
     * @param x Global X coordinate on the virtual plane
     * @param y Global Y coordinate on the virtual plane
     * @return Offset in screen pixel coordinates (Px)
     */
    fun virtualToScreen(deviceId: String, x: Float, y: Float): Offset =
        service.virtualToScreen(deviceId, x, y)

    /**
     * Converts screen pixel coordinates (SC) into virtual coordinates (VU).
     *
     * This is the inverse of virtualToScreen(). Used to convert touch events.
     *
     * @param deviceId Device identifier
     * @param screenX X coordinate in screen pixels
     * @param screenY Y coordinate in screen pixels
     * @return Offset in virtual coordinates (VU)
     */
    fun screenToVirtual(deviceId: String, screenX: Float, screenY: Float): Offset =
        service.screenToVirtual(deviceId, screenX, screenY)

    /**
     * Resets the entire virtual plane system.
     *
     * Clears all configurations, dimensions, and state.
     */
    fun clear() = service.clear()

    /**
     * Creates a reactive stream observing a single device's viewport.
     */
    fun observeViewport(deviceId: String): Flow<Viewport?> =
        service.viewports.map { it[deviceId] }
}