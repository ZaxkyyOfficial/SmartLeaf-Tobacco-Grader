package com.smartleaf.app.ui

import com.smartleaf.app.data.local.SmartLeafResult

sealed class ProcessingState {
    object Idle : ProcessingState()
    object Capturing : ProcessingState()
    object Processing : ProcessingState()
    data class Success(val result: SmartLeafResult) : ProcessingState()
    data class Error(val message: String) : ProcessingState()
}
