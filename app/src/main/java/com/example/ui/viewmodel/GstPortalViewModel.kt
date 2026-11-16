package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.repository.GstPortalApiResponse
import com.example.data.repository.GstPortalRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

sealed interface GstPortalUiState {
    data object Idle : GstPortalUiState
    data object Loading : GstPortalUiState
    data class Error(val message: String) : GstPortalUiState
    data class Result(val result: GstPortalApiResponse) : GstPortalUiState
}

class GstPortalViewModel(
    private val repository: GstPortalRepository = GstPortalRepository(),
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
) : ViewModel() {

    private val _state = MutableStateFlow<GstPortalUiState>(GstPortalUiState.Idle)
    val state: StateFlow<GstPortalUiState> = _state.asStateFlow()

    fun requestOtp(
        profileId: String = "main_1",
        gstin: String,
        gstUsername: String,
    ) {
        launchRequest {
            val token = auth.currentUser?.getIdToken(true)?.await()?.token
                ?: throw IllegalStateException("Please sign in before using GST services.")
            repository.requestOtp(profileId, gstin, gstUsername, token)
        }
    }

    fun verifyOtp(
        profileId: String = "main_1",
        gstin: String,
        gstUsername: String,
        otp: String,
    ) {
        launchRequest {
            val token = auth.currentUser?.getIdToken(true)?.await()?.token
                ?: throw IllegalStateException("Please sign in before using GST services.")
            repository.verifyOtp(profileId, gstin, gstUsername, otp, token)
        }
    }

    fun uploadGstr1(
        profileId: String = "main_1",
        returnPeriod: String,
        returnData: Map<String, Any>,
    ) {
        launchRequest {
            val token = auth.currentUser?.getIdToken(true)?.await()?.token
                ?: throw IllegalStateException("Please sign in before using GST services.")
            repository.uploadGstr1(profileId, returnPeriod, returnData, token)
        }
    }

    private fun launchRequest(block: suspend () -> GstPortalApiResponse) {
        viewModelScope.launch {
            _state.value = GstPortalUiState.Loading
            runCatching { block() }
                .onSuccess { result ->
                    _state.value = GstPortalUiState.Result(result)
                }
                .onFailure { throwable ->
                    _state.value = GstPortalUiState.Error(
                        throwable.message ?: "GST portal request failed."
                    )
                }
        }
    }
}
