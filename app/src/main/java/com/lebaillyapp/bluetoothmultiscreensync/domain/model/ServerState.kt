package com.lebaillyapp.bluetoothmultiscreensync.domain.model

sealed class ServerState {
    object Listening : ServerState()
    object Stopped : ServerState()
    data class Error(val throwable: Throwable) : ServerState()
}