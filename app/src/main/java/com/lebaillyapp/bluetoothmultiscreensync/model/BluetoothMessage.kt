package com.lebaillyapp.bluetoothmultiscreensync.model

import kotlinx.serialization.Serializable

@Serializable
data class BluetoothMessage(
    val id: String,           // unique ID for the image/card
    val x: Float,             // x position in virtual plane
    val y: Float,             // y position in virtual plane
    val width: Float,         // width of the image
    val height: Float,        // height of the image
    val action: Action = Action.MOVE // what to do with this message
) {
    enum class Action {
        MOVE,       // image moved
        ADD,        // new image/card added
        REMOVE      // image/card removed
    }
}