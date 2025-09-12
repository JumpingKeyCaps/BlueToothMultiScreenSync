package com.lebaillyapp.bluetoothmultiscreensync.ui.viewmodel

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

class BluetoothViewModel(
    private val repo: BluetoothRepository
) : ViewModel() {

    // --- Workflow state / events ---
    private val _workflowState = MutableSharedFlow<BluetoothWorkflowState>(replay = 1)
    val workflowState: SharedFlow<BluetoothWorkflowState> = _workflowState.asSharedFlow()

    private val _workflowEvents = MutableSharedFlow<BluetoothWorkflowEvent>(extraBufferCapacity = 16)
    val workflowEvents: SharedFlow<BluetoothWorkflowEvent> = _workflowEvents.asSharedFlow()

    // --- Repository flows ---
    val messages: SharedFlow<String> = repo.messages
    val connectionEvents: SharedFlow<BluetoothConnectionService.ConnectionEvent> = repo.connectionEvents
    val autoConnectState: StateFlow<com.lebaillyapp.bluetoothmultiscreensync.data.service.BluetoothAutoConnectService.AutoConnectState> =
        repo.autoConnectState

    init {
        // Initialize workflow as IDLE
        viewModelScope.launch { _workflowState.emit(BluetoothWorkflowState.IDLE) }
    }

    // === PUBLIC ACTIONS ===
    fun startSequentialWorkflow() {
        viewModelScope.launch {
            _workflowState.emit(BluetoothWorkflowState.CHECKING_PERMISSIONS)
            proceedToNextStep()
        }
    }

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
                else -> {}
            }
        }
    }

    // === INTERNAL WORKFLOW LOGIC ===
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
                }

                BluetoothWorkflowState.READY_TO_SCAN -> {
                    _workflowState.emit(BluetoothWorkflowState.SCANNING)
                    _workflowEvents.emit(BluetoothWorkflowEvent.StartScan)
                    // Démarre auto-connect avec les peers trouvés (initialement vide)
                    repo.startAutoConnect(emptyList())
                }

                else -> {}
            }
        }
    }

    // === WRAPPERS VERS REPO ===
    fun startServer() = repo.startServer()
    fun connectToPeer(peer: BluetoothConnectionService.PeerConnection) = repo.connectToPeer(peer)
    fun sendMessage(msg: String, excludeId: String? = null) = repo.sendMessage(msg, excludeId)
    fun stopAll() = repo.stopAll()
    fun cleanup() = repo.cleanup()
}