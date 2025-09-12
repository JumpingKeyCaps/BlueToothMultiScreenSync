package com.lebaillyapp.bluetoothmultiscreensync.data.service

/**
 * Events System pour Bluetooth
 */
sealed class BluetoothWorkflowEvent {
    // Events émis par Repository vers Screen
    object RequestPermissions : BluetoothWorkflowEvent()
    object RequestBluetoothEnable : BluetoothWorkflowEvent()
    object RequestLocationEnable : BluetoothWorkflowEvent()

    // Events émis par Screen vers Repository
    data class PermissionsResult(val granted: Boolean) : BluetoothWorkflowEvent()
    data class BluetoothEnableResult(val enabled: Boolean) : BluetoothWorkflowEvent()
    data class LocationEnableResult(val enabled: Boolean) : BluetoothWorkflowEvent()

    // Events internes
    object StartScan : BluetoothWorkflowEvent()
    data class WorkflowError(val error: String) : BluetoothWorkflowEvent()
}