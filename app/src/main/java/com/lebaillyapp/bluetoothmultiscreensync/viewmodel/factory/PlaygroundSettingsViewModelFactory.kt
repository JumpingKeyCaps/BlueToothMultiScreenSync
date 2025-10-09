package com.lebaillyapp.bluetoothmultiscreensync.viewmodel.factory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.lebaillyapp.bluetoothmultiscreensync.viewmodel.PlaygroundSettingsViewModel

class PlaygroundSettingsViewModelFactory : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(PlaygroundSettingsViewModel::class.java)) {
            return PlaygroundSettingsViewModel() as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}