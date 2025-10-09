package com.lebaillyapp.bluetoothmultiscreensync.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lebaillyapp.bluetoothmultiscreensync.domain.model.config.ViewportConfig
import com.lebaillyapp.bluetoothmultiscreensync.domain.model.config.VirtualPlaneConfig
import com.lebaillyapp.bluetoothmultiscreensync.domain.model.virtualPlane.VirtualOrientation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * # PlaygroundSettingsViewModel
 * ViewModel responsible for managing the configuration of the virtual plane and its device viewports.
 *
 * This ViewModel provides a reactive API for UI components to:
 * - Observe the virtual plane configuration
 * - Add, remove, move, resize, and rotate individual device viewports
 * - Update the overall virtual plane size (for master devices or autofit scenarios)
 * - Broadcast updated configuration to slave devices
 * - Allow slave devices to change/move and synchronize their viewports with the master
 *
 * All modifications to the virtual plane configuration are thread-safe
 * and propagate updates via [StateFlow].
 */
class PlaygroundSettingsViewModel : ViewModel() {

    /**
     * Backing state for the current virtual plane configuration.
     *
     * Initialized with a default plane size (1000x1000 virtual units) and empty viewport list.
     */
    private val _virtualPlaneConfig = MutableStateFlow(
        VirtualPlaneConfig(
            planeWidth = 100f,
            planeHeight = 100f,
            viewports = emptyList()
        )
    )

    /**
     * Public read-only [StateFlow] exposing the virtual plane configuration.
     *
     * UI components should collect this flow to reactively render the plane and viewport layout.
     */
    val virtualPlaneConfig: StateFlow<VirtualPlaneConfig> = _virtualPlaneConfig

    /**
     * Moves a viewport to a new position within the virtual plane.
     *
     * Typically called when the user drags a viewport rectangle in the configuration UI.
     *
     * @param deviceId Identifier of the device whose viewport should be moved
     * @param newOffsetX New X coordinate of the viewport's top-left corner (in virtual units)
     * @param newOffsetY New Y coordinate of the viewport's top-left corner (in virtual units)
     */
    fun moveViewport(deviceId: String, newOffsetX: Float, newOffsetY: Float) {
        _virtualPlaneConfig.update { plane ->
            val updatedViewports = plane.viewports.map { vp ->
                if (vp.deviceId == deviceId) vp.copy(offsetX = newOffsetX, offsetY = newOffsetY) else vp
            }
            plane.copy(viewports = updatedViewports)
        }
    }

    /**
     * Resizes a viewport to a new width and height.
     *
     * Typically called when the user adjusts the size of a viewport rectangle in the configuration UI.
     *
     * @param deviceId Identifier of the device whose viewport should be resized
     * @param newWidth New width in virtual units
     * @param newHeight New height in virtual units
     */
    fun resizeViewport(deviceId: String, newWidth: Float, newHeight: Float) {
        _virtualPlaneConfig.update { plane ->
            val updatedViewports = plane.viewports.map { vp ->
                if (vp.deviceId == deviceId) vp.copy(width = newWidth, height = newHeight) else vp
            }
            plane.copy(viewports = updatedViewports)
        }
    }

    /**
     * Rotates a viewport to a new virtual orientation.
     *
     * Useful for simulating the device being rotated or flipped within the virtual plane.
     *
     * @param deviceId Identifier of the device whose viewport should be rotated
     * @param newOrientation New virtual orientation (e.g., NORMAL, ROTATED_90, FLIPPED_X, etc.)
     */
    fun rotateViewport(deviceId: String, newOrientation: VirtualOrientation) {
        _virtualPlaneConfig.update { plane ->
            val updatedViewports = plane.viewports.map { vp ->
                if (vp.deviceId == deviceId) vp.copy(orientation = newOrientation) else vp
            }
            plane.copy(viewports = updatedViewports)
        }
    }

    /**
     * Adds a new viewport to the virtual plane configuration.
     *
     * @param viewport The [ViewportConfig] representing the device viewport to add
     */
    fun addViewport(viewport: ViewportConfig) {
        _virtualPlaneConfig.update { plane ->
            plane.copy(viewports = plane.viewports + viewport)
        }
    }

    /**
     * Removes an existing viewport from the virtual plane configuration.
     *
     * @param deviceId Identifier of the device whose viewport should be removed
     */
    fun removeViewport(deviceId: String) {
        _virtualPlaneConfig.update { plane ->
            plane.copy(viewports = plane.viewports.filterNot { it.deviceId == deviceId })
        }
    }

    /**
     * Updates the overall size of the virtual plane.
     *
     * Useful for master devices performing autofit operations or manual resizing.
     *
     * @param newWidth New width of the virtual plane in virtual units
     * @param newHeight New height of the virtual plane in virtual units
     */
    fun updatePlaneSize(newWidth: Float, newHeight: Float) {
        _virtualPlaneConfig.update { plane ->
            plane.copy(planeWidth = newWidth, planeHeight = newHeight)
        }
    }

    /**
     * Broadcasts the current virtual plane configuration to slave devices.
     *
     * In a real Bluetooth / network scenario, this would send the configuration over the communication channel.
     * Here, it accepts a lambda [onSend] to simulate broadcasting.
     *
     * @param onSend Lambda invoked with the current [VirtualPlaneConfig]
     */
    fun broadcastConfigToSlaves(onSend: (VirtualPlaneConfig) -> Unit) {
        viewModelScope.launch {
            onSend(_virtualPlaneConfig.value)
        }
    }
}
