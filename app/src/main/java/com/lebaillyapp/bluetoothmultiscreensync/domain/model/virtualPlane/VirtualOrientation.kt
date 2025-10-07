package com.lebaillyapp.bluetoothmultiscreensync.domain.model.virtualPlane

/**
 * Enum representing the virtual orientation of a viewport.
 * Allows the master device to define a logical orientation
 * that may differ from the physical orientation of the device.
 */
enum class VirtualOrientation {
    NORMAL, ROTATED_90, ROTATED_180, ROTATED_270, FLIPPED_X, FLIPPED_Y
}