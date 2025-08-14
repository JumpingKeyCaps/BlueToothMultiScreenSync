package com.lebaillyapp.bluetoothmultiscreensync.model

/**
 * Represents a device viewport on the virtual canvas.
 *
 * Each connected device has its own viewport which defines the portion of the
 * virtual plane visible on that device's screen. This class provides methods
 * to convert global virtual plane coordinates into local screen coordinates
 * and to check object visibility within the viewport.
 *
 * @property id Unique identifier for the device/viewport.
 * @property screenWidth Width of the device screen in pixels (or virtual units if normalized).
 * @property screenHeight Height of the device screen in pixels (or virtual units if normalized).
 * @property offsetX X-coordinate offset of the viewport in the virtual plane.
 * @property offsetY Y-coordinate offset of the viewport in the virtual plane.
 */
data class Viewport(
    val id: String,
    val screenWidth: Float,
    val screenHeight: Float,
    val offsetX: Float = 0f,
    val offsetY: Float = 0f
) {

    /**
     * Converts a global X-coordinate from the virtual plane to the local X-coordinate
     * relative to this viewport.
     *
     * @param globalX X-coordinate in the virtual plane.
     * @return X-coordinate relative to the viewport's top-left corner.
     */
    fun toLocalX(globalX: Float): Float {
        return globalX - offsetX
    }

    /**
     * Converts a global Y-coordinate from the virtual plane to the local Y-coordinate
     * relative to this viewport.
     *
     * @param globalY Y-coordinate in the virtual plane.
     * @return Y-coordinate relative to the viewport's top-left corner.
     */
    fun toLocalY(globalY: Float): Float {
        return globalY - offsetY
    }

    /**
     * Checks whether a rectangular object is visible within this viewport.
     *
     * @param globalX X-coordinate of the object in the virtual plane.
     * @param globalY Y-coordinate of the object in the virtual plane.
     * @param width Width of the object.
     * @param height Height of the object.
     * @return True if any part of the object is visible in the viewport; false otherwise.
     */
    fun isVisible(globalX: Float, globalY: Float, width: Float, height: Float): Boolean {
        val visibleX = globalX + width > offsetX && globalX < offsetX + screenWidth
        val visibleY = globalY + height > offsetY && globalY < offsetY + screenHeight
        return visibleX && visibleY
    }
}