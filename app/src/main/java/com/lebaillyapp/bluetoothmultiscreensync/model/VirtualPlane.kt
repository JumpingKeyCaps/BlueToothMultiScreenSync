package com.lebaillyapp.bluetoothmultiscreensync.model

/**
 * Represents the global virtual canvas (plan) where all synchronized objects (images/cards) live.
 *
 * This plane defines the coordinate system used across all connected devices.
 *
 * @property width Total width of the virtual plane in virtual units.
 * @property height Total height of the virtual plane in virtual units.
 */
class VirtualPlane(
    val width: Float,
    val height: Float
) {
    /** Internal storage of all SyncObjects keyed by their unique IDs */
    private val objects = mutableMapOf<String, SyncObject>()

    /**
     * Adds a new object to the virtual plane.
     * If an object with the same ID exists, it will be replaced.
     *
     * @param obj The SyncObject to add.
     */
    fun addObject(obj: SyncObject) {
        objects[obj.id] = obj
    }

    /**
     * Removes an object from the virtual plane by its ID.
     *
     * @param id The unique ID of the object to remove.
     */
    fun removeObject(id: String) {
        objects.remove(id)
    }

    /**
     * Updates an existing object on the virtual plane.
     * If the object does not exist, it will be added.
     *
     * @param obj The SyncObject with updated values.
     */
    fun updateObject(obj: SyncObject) {
        objects[obj.id] = obj
    }

    /**
     * Retrieves an object by its ID.
     *
     * @param id The unique ID of the object.
     * @return The SyncObject if found, or null otherwise.
     */
    fun getObject(id: String): SyncObject? = objects[id]

    /**
     * Returns a list of all objects currently on the virtual plane.
     *
     * @return List of all SyncObjects.
     */
    fun getAllObjects(): List<SyncObject> = objects.values.toList()
}