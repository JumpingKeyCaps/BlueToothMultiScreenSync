package com.lebaillyapp.bluetoothmultiscreensync.model

/**
 * Represents a synchronized object (image or card) on the virtual canvas.
 *
 * Coordinates and dimensions are expressed in the virtual plane's coordinate system.
 * This object can be moved or resized, and its state should be propagated to all
 * connected devices via Bluetooth to keep the virtual plane consistent.
 *
 * @property id Unique identifier for this object.
 * @property x X-coordinate of the object's top-left corner on the virtual plane.
 * @property y Y-coordinate of the object's top-left corner on the virtual plane.
 * @property width Width of the object in virtual plane units.
 * @property height Height of the object in virtual plane units.
 */
data class SyncObject(
    val id: String,
    var x: Float,
    var y: Float,
    var width: Float,
    var height: Float
) {

    /**
     * Moves the object to a new position on the virtual plane.
     *
     * @param newX New X-coordinate for the top-left corner.
     * @param newY New Y-coordinate for the top-left corner.
     */
    fun moveTo(newX: Float, newY: Float) {
        x = newX
        y = newY
    }

    /**
     * Resizes the object on the virtual plane.
     *
     * @param newWidth New width of the object.
     * @param newHeight New height of the object.
     */
    fun resize(newWidth: Float, newHeight: Float) {
        width = newWidth
        height = newHeight
    }
}
