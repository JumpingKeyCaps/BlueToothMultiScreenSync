package com.lebaillyapp.bluetoothmultiscreensync.data.repository

import androidx.compose.ui.geometry.Offset
import com.lebaillyapp.bluetoothmultiscreensync.data.virtualPlane.VirtualPlaneService
import com.lebaillyapp.bluetoothmultiscreensync.domain.model.virtualPlane.Viewport
import com.lebaillyapp.bluetoothmultiscreensync.domain.model.virtualPlane.VirtualOrientation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * # VirtualPlaneRepository
 * Repository providing a clean, reactive API for managing the virtual plane coordinate system.
 *
 * This repository acts as an abstraction layer between ViewModels and the [VirtualPlaneService],
 * following the Repository pattern to:
 * - Encapsulate data access logic
 * - Expose immutable reactive streams ([Flow]) instead of mutable state
 * - Provide a simplified API for the presentation layer
 * - Enable easier testing through dependency injection
 *
 *
 * #### Responsibilities
 * - **Coordinate Transformations**: Convert between virtual, local, and screen coordinates
 * - **Viewport Management**: Define, update, and observe device viewport configurations
 * - **Plane Initialization**: Set up the global virtual canvas dimensions
 * - **Reactive State**: Expose observable flows for UI updates
 *
 * #### Thread Safety
 * All operations are thread-safe. The underlying [VirtualPlaneService] uses
 * [kotlinx.coroutines.flow.StateFlow] with atomic updates, making it safe to call
 * from any coroutine context.
 *
 *
 * @property service The underlying [VirtualPlaneService] that manages state
 *
 * @see VirtualPlaneService The service layer managing viewport state
 * @see Viewport Domain model representing a device's viewport configuration
 * @see VirtualOrientation Enum defining possible viewport orientations
 */
class VirtualPlaneRepository(
    private val service: VirtualPlaneService
) {

    /**
     * Reactive stream of the virtual plane's width in virtual units.
     *
     * Emits the current width whenever it changes. Initial value is `0f` before initialization.
     * ViewModels should collect this flow to react to plane size changes.
     *
     * @see planeHeight
     */
    val planeWidth: Flow<Float> = service.planeWidth

    /**
     * Reactive stream of the virtual plane's height in virtual units.
     *
     * Emits the current height whenever it changes. Initial value is `0f` before initialization.
     * ViewModels should collect this flow to react to plane size changes.
     *
     * @see planeWidth
     */
    val planeHeight: Flow<Float> = service.planeHeight

    /**
     * Reactive stream of all device viewports currently configured.
     *
     * Emits a map of device IDs to their corresponding [Viewport] configurations.
     * The map is updated whenever:
     * - A new viewport is added via [defineViewport]
     * - An existing viewport is modified via [updateViewport]
     * - All viewports are cleared via [clear]
     *
     * @return Flow emitting `Map<DeviceId, Viewport>` where keys are device identifiers
     *
     */
    val viewports: Flow<Map<String, Viewport>> = service.viewports

    /**
     * Initializes the global virtual plane dimensions.
     *
     * This should be called **once** by the master device during initial setup.
     * Defines the total coordinate space shared by all connected devices.
     *
     * @param width Total width of the virtual plane in virtual units (must be > 0)
     * @param height Total height of the virtual plane in virtual units (must be > 0)
     * @throws IllegalArgumentException if width or height is <= 0
     *
     * @see defineViewport To assign portions of this plane to devices
     */
    fun initPlane(width: Float, height: Float) {
        service.initPlane(width, height)
    }

    /**
     * Defines a new viewport configuration for a specific device.
     *
     * Creates a mapping between a rectangular region on the virtual plane
     * and a physical device screen. The master device typically calls this
     * for each connected slave during initialization.
     *
     * @param deviceId Unique identifier for the device (typically Bluetooth MAC address)
     * @param offsetX X position of the viewport's top-left corner in virtual units
     * @param offsetY Y position of the viewport's top-left corner in virtual units
     * @param width Width of the viewport in virtual units (must be > 0)
     * @param height Height of the viewport in virtual units (must be > 0)
     * @param screenWidthPx Physical screen width in pixels (must be > 0)
     * @param screenHeightPx Physical screen height in pixels (must be > 0)
     * @param orientation Virtual orientation applied to this viewport (default: NORMAL)
     *
     * @throws IllegalArgumentException if dimensions are invalid or viewport exceeds plane bounds
     *
     * @see updateViewport To modify an existing viewport
     * @see getViewport To retrieve a viewport configuration
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
        service.defineViewport(
            deviceId, offsetX, offsetY, width, height,
            screenWidthPx, screenHeightPx, orientation
        )
    }

    /**
     * Partially updates an existing viewport's configuration.
     *
     * Only non-null parameters are updated; others retain their current values.
     * Useful for adjusting layout without recreating the entire viewport.
     *
     * @param deviceId Unique identifier of the viewport to update
     * @param offsetX New X offset (optional, retains current value if null)
     * @param offsetY New Y offset (optional, retains current value if null)
     * @param width New width in VU (optional, retains current value if null)
     * @param height New height in VU (optional, retains current value if null)
     * @param screenWidthPx New screen width in pixels (optional, retains current if null)
     * @param screenHeightPx New screen height in pixels (optional, retains current if null)
     * @param orientation New virtual orientation (optional, retains current if null)
     *
     * @see defineViewport To create a new viewport
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
        service.updateViewport(
            deviceId, offsetX, offsetY, width, height,
            screenWidthPx, screenHeightPx, orientation
        )
    }

    /**
     * Retrieves the viewport configuration for a specific device.
     *
     * @param deviceId Unique identifier of the device
     * @return The [Viewport] configuration, or `null` if not defined
     *
     *
     * @see observeViewport To reactively observe viewport changes
     */
    fun getViewport(deviceId: String): Viewport? = service.getViewport(deviceId)

    /**
     * Converts global virtual coordinates to local device coordinates.
     *
     * This transformation:
     * 1. Translates coordinates relative to the viewport's offset
     * 2. Applies the device's virtual orientation (rotation/flip)
     * 3. Returns coordinates in the device's local coordinate space (in VU, not pixels)
     *
     * **Note**: This does NOT apply pixel scaling. Use [VirtualPlaneService.virtualToScreen]
     * if you need screen pixel coordinates.
     *
     * @param deviceId Device identifier whose viewport defines the transformation
     * @param x Global X coordinate on the virtual plane
     * @param y Global Y coordinate on the virtual plane
     * @return [Offset] in local device coordinates (still in virtual units)
     *
     * @see VirtualPlaneService.localToVirtual For the inverse transformation
     * @see VirtualPlaneService.virtualToScreen To get screen pixel coordinates
     */
    fun virtualToLocal(deviceId: String, x: Float, y: Float): Offset =
        service.virtualToLocal(deviceId, x, y)

    /**
     * Resets the entire virtual plane system.
     *
     * Clears:
     * - All viewport configurations
     * - Plane dimensions (reset to 0)
     * - Plane state (reset to IDLE)
     *
     * This is typically called when disconnecting from a Bluetooth session
     * or resetting the application state.
     *
     */
    fun clear() = service.clear()

    /**
     * Creates a reactive stream observing a single device's viewport.
     *
     * More efficient than observing [viewports] when you only care about
     * one specific device. Emits `null` if the viewport doesn't exist.
     *
     * @param deviceId Device identifier to observe
     * @return Flow emitting the [Viewport] or `null` whenever it changes
     *
     * @see viewports To observe all viewports simultaneously
     */
    fun observeViewport(deviceId: String): Flow<Viewport?> =
        service.viewports.map { it[deviceId] }
}