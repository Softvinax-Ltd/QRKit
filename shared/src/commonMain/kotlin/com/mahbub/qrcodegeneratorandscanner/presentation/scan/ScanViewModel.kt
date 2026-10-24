package com.mahbub.qrcodegeneratorandscanner.presentation.scan

import androidx.lifecycle.ViewModel
import com.mahbub.qrcodegeneratorandscanner.domain.model.ScannedQr
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class ScanViewModel : ViewModel() {
    private val _state = MutableStateFlow(ScanUiState())
    val state: StateFlow<ScanUiState> = _state.asStateFlow()

    fun onCodeScanned(result: ScannedQr) {
        _state.value = ScanUiState(result = result)
    }

    fun onCameraError(message: String) {
        _state.update { it.copy(errorMessage = message) }
    }

    fun dismissResult() {
        _state.update { it.copy(result = null) }
    }
}
