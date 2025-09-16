package com.lebaillyapp.bluetoothmultiscreensync.ui.viewmodel

import android.bluetooth.BluetoothDevice
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lebaillyapp.bluetoothmultiscreensync.data.repository.BluetoothRepository
import com.lebaillyapp.bluetoothmultiscreensync.data.service.BluetoothConnectionService
import com.lebaillyapp.bluetoothmultiscreensync.data.service.BluetoothWorkflowEvent
import com.lebaillyapp.bluetoothmultiscreensync.model.BluetoothWorkflowState
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class BluetoothViewModel(private val repo: BluetoothRepository) : ViewModel() {

    private val _workflowState = MutableStateFlow(BluetoothWorkflowState.IDLE)
    val workflowState = _workflowState.asStateFlow()

    private val _workflowEvents = MutableSharedFlow<BluetoothWorkflowEvent>(extraBufferCapacity = 16)
    val workflowEvents = _workflowEvents.asSharedFlow()

    val messages = repo.messages
    val connectionEvents = repo.connectionEvents

    init {
        viewModelScope.launch { _workflowState.emit(BluetoothWorkflowState.IDLE) }
    }

    fun startSequentialWorkflow() = viewModelScope.launch { proceedToNextStep() }

    fun handleWorkflowEvent(event: BluetoothWorkflowEvent) {
        viewModelScope.launch {
            when (event) {
                is BluetoothWorkflowEvent.PermissionsResult -> {
                    if (event.granted) _workflowState.emit(BluetoothWorkflowState.CHECKING_BLUETOOTH)
                    else {
                        _workflowState.emit(BluetoothWorkflowState.ERROR)
                        _workflowEvents.emit(BluetoothWorkflowEvent.WorkflowError("Permissions refusées"))
                    }
                    proceedToNextStep()
                }

                is BluetoothWorkflowEvent.BluetoothEnableResult -> {
                    if (event.enabled) _workflowState.emit(BluetoothWorkflowState.CHECKING_LOCATION)
                    else {
                        _workflowState.emit(BluetoothWorkflowState.ERROR)
                        _workflowEvents.emit(BluetoothWorkflowEvent.WorkflowError("Bluetooth refusé"))
                    }
                    proceedToNextStep()
                }

                is BluetoothWorkflowEvent.LocationEnableResult -> {
                    if (event.enabled) _workflowState.emit(BluetoothWorkflowState.READY_TO_SCAN)
                    else _workflowEvents.emit(BluetoothWorkflowEvent.RequestLocationEnable)
                    proceedToNextStep()
                }

                is BluetoothWorkflowEvent.ModeSelected -> {
                    if (event.isServer) startServer()
                    _workflowState.emit(BluetoothWorkflowState.SCANNING)
                    _workflowEvents.emit(BluetoothWorkflowEvent.StartScan)
                }

                else -> {}
            }
        }
    }

    private fun proceedToNextStep() {
        viewModelScope.launch {
            when (_workflowState.value) {
                BluetoothWorkflowState.IDLE,
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

                BluetoothWorkflowState.READY_TO_SCAN -> {
                    _workflowState.emit(BluetoothWorkflowState.SCANNING)
                    _workflowEvents.emit(BluetoothWorkflowEvent.StartScan)
                }

                else -> {}
            }
        }
    }

    // Wrappers repo
    fun startServer() = repo.startServer()
    fun connectToPeer(peer: BluetoothConnectionService.PeerConnection) = repo.connectToPeer(peer)
    fun sendMessage(msg: String, excludeId: String? = null) = repo.sendMessage(msg, excludeId)
    fun stopAll() = repo.stopAll()
    fun cleanup() = repo.cleanup()
}