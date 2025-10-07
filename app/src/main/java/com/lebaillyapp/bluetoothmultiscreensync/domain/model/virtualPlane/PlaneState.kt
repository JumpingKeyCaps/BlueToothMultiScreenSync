package com.lebaillyapp.bluetoothmultiscreensync.domain.model.virtualPlane

/**
 * Optional state of the virtual plane system.
 *
 * Can help ViewModels track readiness or sync status.
 */
enum class PlaneState {
    IDLE,    // Not initialized yet
    READY,   // Plane + viewports initialized locally
    SYNCED   // Plane + viewports received from Master and fully synced
}