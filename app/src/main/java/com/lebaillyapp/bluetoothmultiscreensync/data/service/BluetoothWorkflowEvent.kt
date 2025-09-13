package com.lebaillyapp.bluetoothmultiscreensync.data.service

/**
 * ## BluetoothWorkflowEvent
 * A sealed class representing the various events that occur during the Bluetooth
 * workflow. This provides a type-safe way to manage the state and transitions
 * required for a complete Bluetooth connection flow, including permission requests,
 * state changes, and errors.
 */
sealed class BluetoothWorkflowEvent {
    // ## Events emitted from Repository to Screen
    /**
     * An event requesting the UI to ask the user for necessary permissions (e.g., Bluetooth, Location).
     */
    object RequestPermissions : BluetoothWorkflowEvent()
    /**
     * An event requesting the UI to prompt the user to enable Bluetooth.
     */
    object RequestBluetoothEnable : BluetoothWorkflowEvent()
    /**
     * An event requesting the UI to prompt the user to enable location services,
     * which is often required for Bluetooth scanning on Android.
     */
    object RequestLocationEnable : BluetoothWorkflowEvent()

    // ## Events emitted from Screen to Repository
    /**
     * A data class event containing the result of a permission request.
     *
     * @property granted A boolean indicating if the permissions were granted.
     */
    data class PermissionsResult(val granted: Boolean) : BluetoothWorkflowEvent()
    /**
     * A data class event containing the result of a Bluetooth enable request.
     *
     * @property enabled A boolean indicating if Bluetooth was successfully enabled.
     */
    data class BluetoothEnableResult(val enabled: Boolean) : BluetoothWorkflowEvent()
    /**
     * A data class event containing the result of a location enable request.
     *
     * @property enabled A boolean indicating if location services were successfully enabled.
     */
    data class LocationEnableResult(val enabled: Boolean) : BluetoothWorkflowEvent()

    // ## Internal Events
    /**
     * An internal event to signal the start of a Bluetooth device scan.
     */
    object StartScan : BluetoothWorkflowEvent()
    /**
     * A data class event to signal that an error has occurred during the workflow.
     *
     * @property error A descriptive string of the error.
     */
    data class WorkflowError(val error: String) : BluetoothWorkflowEvent()
}