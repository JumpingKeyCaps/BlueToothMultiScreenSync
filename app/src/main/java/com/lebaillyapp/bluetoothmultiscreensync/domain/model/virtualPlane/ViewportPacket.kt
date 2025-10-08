package com.lebaillyapp.bluetoothmultiscreensync.domain.model.virtualPlane

import kotlinx.serialization.Serializable

/**
 * # ViewportPacket
 * Serializable data transfer object representing a viewport configuration for network transmission.
 *
 * This packet is used for Bluetooth communication between the master device and slave devices
 * to synchronize viewport configurations. It contains all necessary information to define
 * how a specific device should map its portion of the virtual canvas.
 *
 * ## Usage Flow
 * 1. **Master Device**: Creates `ViewportPacket` instances for each connected slave
 * 2. **Serialization**: Packet is serialized (JSON or binary) for Bluetooth transmission
 * 3. **Transmission**: Sent via Bluetooth Classic SPP (RFCOMM) channel
 * 4. **Slave Device**: Receives and deserializes the packet
 * 5. **Conversion**: Calls `toViewport()` to create a domain model instance
 *
 * ## Protocol Context
 * This packet is typically sent:
 * - During initial handshake after Bluetooth pairing
 * - When the master reconfigures the virtual plane layout
 * - When a device's screen dimensions change (e.g., rotation, multi-window)
 *
 * The master device is the single source of truth for all viewport assignments,
 * ensuring consistent coordinate mapping across the entire virtual plane.
 *
 * @property deviceId Unique identifier of the target device (typically Bluetooth MAC address)
 * @property offsetX X position of the viewport's top-left corner on the virtual plane (in VU)
 * @property offsetY Y position of the viewport's top-left corner on the virtual plane (in VU)
 * @property width Width of the viewport in virtual units
 * @property height Height of the viewport in virtual units
 * @property orientation Virtual orientation assigned by the master device
 * @property screenWidthPx Physical screen width of the device in pixels
 * @property screenHeightPx Physical screen height of the device in pixels
 *
 *
 * @see Viewport The domain model representation used internally by the app
 * @see VirtualOrientation Available orientation configurations
 * @see com.lebaillyapp.bluetoothmultiscreensync.data.virtualPlane.VirtualPlaneService
 */
@Serializable
data class ViewportPacket(
    val deviceId: String,
    val offsetX: Float,
    val offsetY: Float,
    val width: Float,
    val height: Float,
    val orientation: VirtualOrientation,
    val screenWidthPx: Int,
    val screenHeightPx: Int
) {
    /**
     * Converts this network packet into a domain model [Viewport] instance.
     *
     * This conversion is typically performed by slave devices after receiving
     * viewport configuration from the master device via Bluetooth.
     *
     * The resulting [Viewport] object can be used to initialize the local
     * [VirtualPlaneService] and establish the device's coordinate mapping.
     *
     * @return A [Viewport] instance with identical configuration values
     *
     */
    fun toViewport() = Viewport(
        deviceId, offsetX, offsetY, width, height,
        orientation, screenWidthPx, screenHeightPx
    )
}