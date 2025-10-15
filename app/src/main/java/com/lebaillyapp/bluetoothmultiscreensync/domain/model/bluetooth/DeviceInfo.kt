package com.lebaillyapp.bluetoothmultiscreensync.domain.model.bluetooth

data class DeviceInfo(
    val name: String,
    val address: String,
    val isMaster: Boolean = false
)