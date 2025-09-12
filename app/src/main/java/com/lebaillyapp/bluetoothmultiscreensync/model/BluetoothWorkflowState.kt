package com.lebaillyapp.bluetoothmultiscreensync.model

/**
 * Statut du workflow Bluetooth
 */
enum class BluetoothWorkflowState {
    IDLE,
    CHECKING_PERMISSIONS,
    REQUESTING_PERMISSIONS,
    CHECKING_BLUETOOTH,
    REQUESTING_BLUETOOTH,
    CHECKING_LOCATION,
    REQUESTING_LOCATION,
    READY_TO_SCAN,
    SCANNING,
    ERROR
}