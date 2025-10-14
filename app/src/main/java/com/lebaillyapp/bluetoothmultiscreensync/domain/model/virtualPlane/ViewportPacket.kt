package com.lebaillyapp.bluetoothmultiscreensync.data.transfer // NOTE: J'ai déplacé le paquet à 'data.transfer' car c'est un DTO réseau, pas un modèle de domaine pur.

import com.lebaillyapp.bluetoothmultiscreensync.domain.model.virtualPlane.Viewport
import com.lebaillyapp.bluetoothmultiscreensync.domain.model.virtualPlane.VirtualOrientation
import kotlinx.serialization.Serializable

/**
 * # ViewportPacket
 * Serializable data transfer object (DTO) representing a viewport configuration for network transmission.
 *
 * This packet is used for Bluetooth communication between the master device and slave devices
 * to synchronize viewport configurations. It contains all necessary information to define
 * how a specific device should map its portion of the virtual canvas to its physical screen.
 *
 * ## Scaling Context
 * The inclusion of screen dimensions in **Density-Independent Pixels (DP)** and the device's
 * **density** ensures that the receiving Slave device can correctly establish the local
 * scaling (VU ↔ DP ↔ Px) required for consistent **physical sizing** of objects across all screens.
 *
 * ## Usage Flow
 * 1. **Master Device**: Creates and serializes `ViewportPacket` instances.
 * 2. **Transmission**: Sent via Bluetooth.
 * 3. **Slave Device**: Receives, deserializes the packet, and calls `toViewport()` to build
 * the domain model used by the local [VirtualPlaneService].
 *
 * @property deviceId Unique identifier of the target device (e.g., Bluetooth MAC address)
 * @property offsetX X position of the viewport's top-left corner on the virtual plane (in VU)
 * @property offsetY Y position of the viewport's top-left corner on the virtual plane (in VU)
 * @property width Width of the viewport in virtual units
 * @property height Height of the viewport in virtual units
 * @property orientation Virtual orientation assigned by the master device
 * @property screenWidthDp Device screen width in **Density-Independent Pixels (DP)** (Float)
 * @property screenHeightDp Device screen height in **Density-Independent Pixels (DP)** (Float)
 * @property density Device pixel density ratio (Px/DP)
 *
 * @see Viewport The domain model representation used internally by the app
 */
@Serializable
data class ViewportPacket(
    val deviceId: String,
    val offsetX: Float,
    val offsetY: Float,
    val width: Float,
    val height: Float,
    val orientation: VirtualOrientation,
    val screenWidthDp: Float, // Correction: Remplacé Int Px par Float DP
    val screenHeightDp: Float, // Correction: Remplacé Int Px par Float DP
    val density: Float // Correction: Ajouté la densité
) {
    /**
     * Converts this network packet into a domain model [Viewport] instance.
     *
     * @return A [Viewport] instance with all configuration values, including DP dimensions
     * and density required for local scale calculations.
     */
    fun toViewport() = Viewport(
        deviceId, offsetX, offsetY, width, height,
        orientation, screenWidthDp, screenHeightDp, density
    )
}