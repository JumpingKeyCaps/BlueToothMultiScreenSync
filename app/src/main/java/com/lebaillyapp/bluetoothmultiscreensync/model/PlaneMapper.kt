package com.lebaillyapp.bluetoothmultiscreensync.model

import android.graphics.RectF

/**
 * Utility object that maps [SyncObject]s from the [VirtualPlane] into
 * local rectangles for a given [Viewport].
 *
 * This avoids duplicating conversion and clipping logic in the UI layer.
 */
object PlaneMapper {

    /**
     * Maps all objects from the virtual plane into their corresponding
     * local screen rectangles for the given viewport.
     *
     * - Invisible objects are filtered out.
     * - Only visible objects are returned.
     *
     * @param plane The global virtual plane.
     * @param viewport The device viewport.
     * @return List of pairs (object, local rectangle).
     */
    fun mapObjects(
        plane: VirtualPlane,
        viewport: Viewport
    ): List<Pair<SyncObject, RectF>> {
        return plane.getAllObjects().mapNotNull { obj ->
            viewport.project(obj)?.let { rect -> obj to rect }
        }
    }
}