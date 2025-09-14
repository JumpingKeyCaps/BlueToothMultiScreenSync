package com.lebaillyapp.bluetoothmultiscreensync.model

/**
 * ## BluetoothWorkflowState
 * An enumeration class representing the various states of the Bluetooth workflow.
 * It provides a clear, finite set of states for the UI to reflect the current
 * progress and status of the Bluetooth setup process, from initial checks to
 * active scanning or error states.
 */
enum class BluetoothWorkflowState {
    /**
     * The initial state, where the workflow is not active.
     */
    IDLE,
    /**
     * The state where the application is checking for required permissions.
     */
    CHECKING_PERMISSIONS,
    /**
     * The state where the application is actively requesting permissions from the user.
     */
    REQUESTING_PERMISSIONS,
    /**
     * The state where the application is checking if Bluetooth is enabled on the device.
     */
    CHECKING_BLUETOOTH,
    /**
     * The state where the application is prompting the user to enable Bluetooth.
     */
    REQUESTING_BLUETOOTH,
    /**
     * The state where the application is checking if location services are enabled.
     */
    CHECKING_LOCATION,
    /**
     * The state where the application is prompting the user to enable location services.
     */
    REQUESTING_LOCATION,
    /**
     * The state where the application waits for the user to select the Bluetooth mode (Client or Server)
     * before starting the scanning process.
     */
    SELECT_MODE,
    /**
     * The state where all prerequisites are met and the device is ready to start scanning for peers.
     */
    READY_TO_SCAN,
    /**
     * The state where the application is actively scanning for other Bluetooth devices.
     */
    SCANNING,
    /**
     * The state indicating that an error has occurred in the workflow.
     */
    ERROR
}