package com.mahbub.qrcodegeneratorandscanner.presentation.generate

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mahbub.qrcodegeneratorandscanner.domain.model.QrErrorLevel
import com.mahbub.qrcodegeneratorandscanner.domain.usecase.GenerateQrCodeUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class GenerateViewModel(
    private val generateQrCode: GenerateQrCodeUseCase,
) : ViewModel() {
    private val _state = MutableStateFlow(GenerateUiState())
    val state: StateFlow<GenerateUiState> = _state.asStateFlow()

    private var generationJob: Job? = null

    init {
        regenerate()
    }

    fun onContentChanged(content: String) {
        if (content == _state.value.content) return
        _state.update { it.copy(content = content) }
        regenerate()
    }

    fun onErrorLevelChanged(errorLevel: QrErrorLevel) {
        if (errorLevel == _state.value.errorLevel) return
        _state.update { it.copy(errorLevel = errorLevel) }
        regenerate()
    }

    private fun regenerate() {
        generationJob?.cancel()

        val content = _state.value.content
        val errorLevel = _state.value.errorLevel
        if (content.isBlank()) {
            _state.update { it.copy(generated = null, errorMessage = null) }
            return
        }

        generationJob = viewModelScope.launch {
            delay(INPUT_DEBOUNCE_MILLIS)
            val result = withContext(Dispatchers.Default) {
                generateQrCode(content, errorLevel)
            }
            _state.update { current ->
                if (current.content != content || current.errorLevel != errorLevel) {
                    current
                } else {
                    result.fold(
                        onSuccess = { current.copy(generated = it, errorMessage = null) },
                        onFailure = {
                            current.copy(
                                generated = null,
                                errorMessage = it.message ?: "Unable to encode this payload",
                            )
                        },
                    )
                }
            }
        }
    }

    private companion object {
        const val INPUT_DEBOUNCE_MILLIS = 150L
    }
}
