package com.lebaillyapp.bluetoothmultiscreensync.domain.model.virtualPlane

/**
 * Data class representing a viewport assigned to a device.
 *
 * @property deviceId Unique identifier of the device
 * @property offsetX X position of the viewport on the virtual plane
 * @property offsetY Y position of the viewport on the virtual plane
 * @property width Width of the viewport in virtual units
 * @property height Height of the viewport in virtual units
 * @property orientation Virtual orientation applied to the viewport
 */
data class Viewport(
    val deviceId: String,
    var offsetX: Float,
    var offsetY: Float,
    var width: Float,
    var height: Float,
    var orientation: VirtualOrientation = VirtualOrientation.NORMAL
)