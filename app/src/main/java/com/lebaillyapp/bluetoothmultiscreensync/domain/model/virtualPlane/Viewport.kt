package com.lebaillyapp.bluetoothmultiscreensync.domain.model.virtualPlane

import kotlin.math.min

/**
 * # Viewport
 *
 * Represents the portion of the global **virtual plane** assigned to a specific device.
 *
 * Each device receives a `Viewport` describing which part of the virtual canvas it renders,
 * and how virtual coordinates (in **VU**) map to its own display space (in **DP**).
 *
 * ---
 *
 * ## Coordinate Systems
 * - **VU (Virtual Units)** → Global abstract coordinate space, identical for all devices.
 * - **DP (Density-independent Pixels)** → Logical Android display units ensuring the same
 *   physical size on screens of different densities.
 * - **PX (Physical Pixels)** → Device-dependent pixel grid used internally by Android’s renderer.
 *
 * ---
 *
 * ## Scaling Strategy
 *
 * The viewport defines a **uniform scale factor** that converts Virtual Units → DP.
 * This ensures consistent *physical size* across devices, regardless of screen DPI.
 *
 * The conversion is based on the device’s *logical density* (dp per inch), so that:
 *
 * ```
 * scaleDpPerVu = min(screenWidthDp / widthVu, screenHeightDp / heightVu)
 * ```
 *
 * Then each device’s rendering pipeline (Compose or Canvas) handles the
 * final DP → PX conversion automatically.
 *
 * ---
 *
 * ## Master–Slave Usage
 *
 * - The **Master** defines all `Viewport` areas in Virtual Units (VU).
 * - Each **Slave** sends its screen size in DP to the Master during handshake.
 * - The Master computes and assigns a consistent scale (`Dp/VU`) for every device.
 *
 * ---
 *
 * ## Virtual Orientation
 *
 * A `VirtualOrientation` may be applied by the Master to position the device
 * logically in the multi-screen layout, without requiring physical rotation.
 *
 * ---
 *
 * @property deviceId Unique identifier of the device (e.g. Bluetooth MAC address)
 * @property offsetX X position of the viewport’s top-left corner on the virtual plane (in VU)
 * @property offsetY Y position of the viewport’s top-left corner on the virtual plane (in VU)
 * @property width Width of the viewport in virtual units
 * @property height Height of the viewport in virtual units
 * @property orientation Logical orientation assigned by the Master
 * @property screenWidthDp Device screen width in density-independent pixels (dp)
 * @property screenHeightDp Device screen height in density-independent pixels (dp)
 * @property density Device pixel density (`dpi / 160f`)
 *
 * @property scaleDpPerVu Uniform scaling factor (dp per virtual unit),
 *                        ensuring equal physical size across all screens.
 *
 * ---
 *
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
    val screenWidthDp: Float,
    val screenHeightDp: Float,
    val density: Float
) {

    /**
     * Uniform scale factor (in dp per VU).
     *
     * Ensures that all devices display objects at the same *physical size*
     * regardless of their screen density (DPI) or pixel resolution.
     *
     * Formula:
     * ```
     * scaleDpPerVu = min(screenWidthDp / widthVu, screenHeightDp / heightVu)
     * ```
     *
     * @return Scale factor in dp/VU
     */
    val scaleDpPerVu: Float
        get() = min(
            screenWidthDp / width,
            screenHeightDp / height
        )

    /**
     * Converts a value from Virtual Units (VU) to Density-independent Pixels (DP).
     * Use this when rendering elements defined in the virtual coordinate space.
     */
    fun vuToDp(valueVu: Float): Float = valueVu * scaleDpPerVu

    /**
     * Converts a value from Virtual Units (VU) directly to physical pixels (PX).
     * This can be useful for low-level rendering (e.g. shaders, Canvas).
     */
    fun vuToPx(valueVu: Float): Float = valueVu * scaleDpPerVu * density
}