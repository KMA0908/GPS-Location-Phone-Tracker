package com.nhn.gpstracker.base

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

abstract class BaseViewModel : ViewModel() {

    private val _messages = MutableSharedFlow<UiMessage>(extraBufferCapacity = 1)
    val messages: SharedFlow<UiMessage> = _messages.asSharedFlow()

    protected fun launchCatching(block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (throwable: Throwable) {
                _messages.emit(
                    UiMessage.Error(
                        throwable.message ?: "Something went wrong",
                    ),
                )
            }
        }
    }
}

sealed interface UiMessage {
    data class Error(val message: String) : UiMessage
    data class Info(val message: String) : UiMessage
}
