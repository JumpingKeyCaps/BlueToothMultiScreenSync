package com.lebaillyapp.bluetoothmultiscreensync.domain.model.virtualPlane

/**
 * # Viewport
 * Data class representing a viewport assigned to a device within the virtual plane system.
 *
 * A viewport defines the portion of the global virtual canvas that a specific device
 * is responsible for rendering. It maps a rectangular region in virtual units (VU)
 * to the physical screen dimensions of the device.
 *
 * ## Coordinate Systems
 * - **Virtual Units (VU)**: Abstract coordinate system shared across all devices
 * - **Screen Pixels (SC)**: Physical pixel coordinates of the device screen
 *
 * ## Scaling Strategy
 * The viewport calculates a uniform scale factor to map virtual units to screen pixels
 * while preserving aspect ratio. This ensures that objects appear at the same physical
 * size across devices with different screen densities and resolutions.
 *
 * ## Virtual Orientation
 * The master device can assign a logical orientation to each viewport, allowing devices
 * to be positioned in any configuration (rotated, flipped) without requiring physical
 * device rotation.
 *
 * @property deviceId Unique identifier of the device (typically Bluetooth MAC address)
 * @property offsetX X position of the viewport's top-left corner on the virtual plane (in VU)
 * @property offsetY Y position of the viewport's top-left corner on the virtual plane (in VU)
 * @property width Width of the viewport in virtual units
 * @property height Height of the viewport in virtual units
 * @property orientation Virtual orientation applied to this viewport by the master device
 * @property screenWidthPx Physical screen width of the device in pixels
 * @property screenHeightPx Physical screen height of the device in pixels
 *
 * @property scale Uniform scale factor (pixels per virtual unit) calculated as the minimum
 *                 of horizontal and vertical scale ratios to prevent distortion.
 *                 Formula: `min(screenWidthPx / width, screenHeightPx / height)`
 *
 * ## Example
 * ```kotlin
 * val viewport = Viewport(
 *     deviceId = "00:11:22:33:44:55",
 *     offsetX = 0f,
 *     offsetY = 0f,
 *     width = 1000f,
 *     height = 2000f,
 *     orientation = VirtualOrientation.NORMAL,
 *     screenWidthPx = 1080,
 *     screenHeightPx = 2400
 * )
 *
 * // Scale factor: min(1080/1000, 2400/2000) = min(1.08, 1.2) = 1.08
 * println(viewport.scale) // 1.08
 * ```
 *
 * @see VirtualOrientation
 */
data class Viewport(
    val deviceId: String,
    val offsetX: Float,
    val offsetY: Float,
    val width: Float,
    val height: Float,
    val orientation: VirtualOrientation = VirtualOrientation.NORMAL,
    val screenWidthPx: Int,
    val screenHeightPx: Int
) {
    /**
     * Uniform scale factor for converting virtual units to screen pixels.
     *
     * This scale factor ensures that:
     * - Objects maintain consistent physical size across devices
     * - Aspect ratio is preserved (no distortion)
     * - The entire viewport fits within the screen bounds
     *
     * The scale is calculated as the minimum ratio between screen dimensions
     * and viewport dimensions to prevent any overflow or stretching.
     *
     * @return Pixels per virtual unit (px/VU)
     */
    val scale: Float
        get() = minOf(
            screenWidthPx / width,
            screenHeightPx / height
        )
}