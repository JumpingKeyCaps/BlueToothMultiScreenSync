package com.lebaillyapp.bluetoothmultiscreensync.ui.viewmodel

import android.bluetooth.BluetoothDevice
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lebaillyapp.bluetoothmultiscreensync.data.repository.BluetoothRepository
import com.lebaillyapp.bluetoothmultiscreensync.data.service.BluetoothConnectionService
import com.lebaillyapp.bluetoothmultiscreensync.data.service.BluetoothWorkflowEvent
import com.lebaillyapp.bluetoothmultiscreensync.model.BluetoothWorkflowState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ## BluetoothViewModel
 * A ViewModel responsible for managing the state and logic of the Bluetooth workflow.
 * It acts as a bridge between the UI (Compose) and the data layer (Repository),
 * handling permission requests, state transitions, and communication with other devices.
 *
 * @property repo The [BluetoothRepository] instance providing access to the Bluetooth data services.
 */
class BluetoothViewModel(
    private val repo: BluetoothRepository
) : ViewModel() {

    // --- Workflow state / events ---
    /**
     * A [MutableSharedFlow] representing the current state of the Bluetooth setup workflow.
     * It's configured with `replay = 1` to ensure new subscribers immediately receive the latest state.
     */
    private val _workflowState = MutableSharedFlow<BluetoothWorkflowState>(replay = 1)
    /**
     * The public-facing [SharedFlow] for the UI to observe the workflow state.
     */
    val workflowState: SharedFlow<BluetoothWorkflowState> = _workflowState.asSharedFlow()

    /**
     * A [MutableSharedFlow] for emitting one-time events to the UI, such as requests to open system settings.
     * It has an extra buffer capacity of 16 to prevent loss of events.
     */
    private val _workflowEvents = MutableSharedFlow<BluetoothWorkflowEvent>(extraBufferCapacity = 16)
    /**
     * The public-facing [SharedFlow] for the UI to collect one-time workflow events.
     */
    val workflowEvents: SharedFlow<BluetoothWorkflowEvent> = _workflowEvents.asSharedFlow()

    // --- Repository flows ---
    /**
     * Exposes the stream of incoming messages from the repository.
     */
    val messages: SharedFlow<String> = repo.messages
    /**
     * Exposes the stream of connection-related events from the repository.
     */
    val connectionEvents: SharedFlow<BluetoothConnectionService.ConnectionEvent> = repo.connectionEvents


    /**
     * ## init
     * Initializes the ViewModel by setting the initial workflow state to [BluetoothWorkflowState.IDLE].
     */
    init {
        // Initialize workflow as IDLE
        viewModelScope.launch { _workflowState.emit(BluetoothWorkflowState.IDLE) }
    }

    // === PUBLIC ACTIONS ===
    /**
     * ## startSequentialWorkflow
     * Starts the sequential Bluetooth setup workflow.
     * This method should be called by the UI to begin the process, starting with permission checks.
     */
    fun startSequentialWorkflow() {
        viewModelScope.launch {
            _workflowState.emit(BluetoothWorkflowState.CHECKING_PERMISSIONS)
            proceedToNextStep()
        }
    }

    /**
     * ## handleWorkflowEvent
     * Handles events sent from the UI as a result of user actions or system callbacks (e.g., permission results).
     * This method drives the workflow to the next step based on the event outcome.
     *
     * @param event The [BluetoothWorkflowEvent] received from the UI.
     */
    fun handleWorkflowEvent(event: BluetoothWorkflowEvent) {
        viewModelScope.launch {
            when (event) {
                is BluetoothWorkflowEvent.PermissionsResult -> {
                    if (event.granted) {
                        _workflowState.emit(BluetoothWorkflowState.CHECKING_BLUETOOTH)
                        proceedToNextStep()
                    } else {
                        _workflowState.emit(BluetoothWorkflowState.ERROR)
                        _workflowEvents.emit(BluetoothWorkflowEvent.WorkflowError("Permissions refusées"))
                    }
                }
                is BluetoothWorkflowEvent.BluetoothEnableResult -> {
                    if (event.enabled) {
                        _workflowState.emit(BluetoothWorkflowState.CHECKING_LOCATION)
                        delay(300)
                        proceedToNextStep()
                    } else {
                        _workflowState.emit(BluetoothWorkflowState.ERROR)
                        _workflowEvents.emit(BluetoothWorkflowEvent.WorkflowError("Bluetooth refusé"))
                    }
                }
                is BluetoothWorkflowEvent.LocationEnableResult -> {
                    if (event.enabled) {
                        _workflowState.emit(BluetoothWorkflowState.READY_TO_SCAN)
                        delay(300)
                        proceedToNextStep()
                    } else {
                        // Retry location
                        _workflowState.emit(BluetoothWorkflowState.CHECKING_LOCATION)
                        delay(300)
                        proceedToNextStep()
                    }
                }
                is BluetoothWorkflowEvent.ModeSelected -> {
                    if (event.isServer) {
                        startServer()
                    } else {
                        _workflowState.emit(BluetoothWorkflowState.READY_TO_SCAN)
                    }
                    proceedToNextStep()
                }
                else -> {}
            }
        }
    }

    // === INTERNAL WORKFLOW LOGIC ===
    /**
     * ## proceedToNextStep
     * A private helper function that determines the next step in the workflow based on the current state.
     * It emits the next state and sends a corresponding event to the UI for action.
     */
    private fun proceedToNextStep() {
        viewModelScope.launch {
            when (_workflowState.replayCache.lastOrNull() ?: BluetoothWorkflowState.IDLE) {
                BluetoothWorkflowState.CHECKING_PERMISSIONS -> {
                    _workflowState.emit(BluetoothWorkflowState.REQUESTING_PERMISSIONS)
                    _workflowEvents.emit(BluetoothWorkflowEvent.RequestPermissions)
                }

                BluetoothWorkflowState.CHECKING_BLUETOOTH -> {
                    _workflowState.emit(BluetoothWorkflowState.REQUESTING_BLUETOOTH)
                    _workflowEvents.emit(BluetoothWorkflowEvent.RequestBluetoothEnable)
                }

                BluetoothWorkflowState.CHECKING_LOCATION -> {
                    _workflowState.emit(BluetoothWorkflowState.REQUESTING_LOCATION)
                    _workflowEvents.emit(BluetoothWorkflowEvent.RequestLocationEnable)
                    _workflowEvents.emit(BluetoothWorkflowEvent.RequestModeSelection)
                }

                BluetoothWorkflowState.SELECT_MODE -> {
                    _workflowEvents.emit(BluetoothWorkflowEvent.RequestModeSelection)
                }

                BluetoothWorkflowState.READY_TO_SCAN -> {
                    _workflowState.emit(BluetoothWorkflowState.SCANNING)
                    _workflowEvents.emit(BluetoothWorkflowEvent.StartScan)

                }

                else -> {}
            }
        }
    }

    // === WRAPPERS VERS REPO ===





    /**
     * ## startServer
     * Wrapper for [BluetoothRepository.startServer].
     * Starts the Bluetooth service in server mode.
     */
    fun startServer() = repo.startServer()
    /**
     * ## connectToPeer
     * Wrapper for [BluetoothRepository.connectToPeer].
     * Initiates a connection to a specific peer.
     *
     * @param peer The peer to connect to.
     */
    fun connectToPeer(peer: BluetoothConnectionService.PeerConnection) = repo.connectToPeer(peer)
    /**
     * ## sendMessage
     * Wrapper for [BluetoothRepository.sendMessage].
     * Sends a message to all connected peers.
     *
     * @param msg The message to send.
     * @param excludeId The ID of the peer to exclude.
     */
    fun sendMessage(msg: String, excludeId: String? = null) = repo.sendMessage(msg, excludeId)
    /**
     * ## stopAll
     * Wrapper for [BluetoothRepository.stopAll].
     * Stops all active connections and the auto-connect service.
     */
    fun stopAll() = repo.stopAll()
    /**
     * ## cleanup
     * Wrapper for [BluetoothRepository.cleanup].
     * Cleans up all resources held by the repository.
     */
    fun cleanup() = repo.cleanup()
}