package com.lebaillyapp.bluetoothmultiscreensync.model

import android.graphics.RectF

/**
 * Represents a device viewport on the virtual canvas.
 *
 * Each connected device has its own viewport which defines the portion of the
 * virtual plane visible on that device's screen. This class provides methods
 * to convert global virtual plane coordinates into local screen coordinates
 * and to check object visibility within the viewport.
 *
 * @property id Unique identifier for the device/viewport.
 * @property screenWidth Width of the device screen in pixels.
 * @property screenHeight Height of the device screen in pixels.
 * @property widthVU Width of the viewport expressed in virtual units (defined by the master).
 * @property heightVU Height of the viewport expressed in virtual units (defined by the master).
 * @property offsetX X-coordinate offset of the viewport in the virtual plane (top-left corner).
 * @property offsetY Y-coordinate offset of the viewport in the virtual plane (top-left corner).
 */
data class Viewport(
    val id: String,
    val screenWidth: Float,
    val screenHeight: Float,
    val widthVU: Float,
    val heightVU: Float,
    val offsetX: Float = 0f,
    val offsetY: Float = 0f
) {
    /** Horizontal scale factor between virtual units and screen pixels. */
    val scaleX: Float get() = screenWidth / widthVU

    /** Vertical scale factor between virtual units and screen pixels. */
    val scaleY: Float get() = screenHeight / heightVU
}

/**
 * Projects a [SyncObject] from the virtual plane into local screen coordinates
 * for this [Viewport].
 *
 * - Converts virtual coordinates into pixels.
 * - Handles cropping if the object is partially visible.
 * - Returns `null` if the object is completely outside the viewport.
 *
 * @receiver The viewport into which the object should be mapped.
 * @param obj The synchronized object to map.
 * @return A [RectF] in local screen coordinates, or null if not visible.
 */
fun Viewport.project(obj: SyncObject): RectF? {
    val scale = scaleX // Assumes scaleX == scaleY to prevent distortion.

    val localX = (obj.x - offsetX) * scale
    val localY = (obj.y - offsetY) * scale
    val localW = obj.width * scale
    val localH = obj.height * scale

    // Check if completely outside
    if (localX + localW <= 0 || localY + localH <= 0 ||
        localX >= screenWidth || localY >= screenHeight) {
        return null
    }

    // Apply clipping
    val clippedX = localX.coerceIn(0f, screenWidth)
    val clippedY = localY.coerceIn(0f, screenHeight)
    val clippedW = (localX + localW).coerceIn(0f, screenWidth) - clippedX
    val clippedH = (localY + localH).coerceIn(0f, screenHeight) - clippedY

    return RectF(clippedX, clippedY, clippedX + clippedW, clippedY + clippedH)
}
