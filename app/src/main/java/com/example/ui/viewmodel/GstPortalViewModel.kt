package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.repository.GstPortalApiResponse
import com.example.data.repository.GstPortalRepository
import com.google.firebase.auth.FirebaseAuth
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

    fun clearState() { _state.value = GstPortalUiState.Idle }

    fun requestOtp(profileId: String, gstin: String, username: String, email: String) = launchRequest {
        repository.requestOtp(profileId, gstin, username, email, firebaseToken())
    }

    fun verifyOtp(profileId: String, gstin: String, username: String, email: String, otp: String) = launchRequest {
        repository.verifyOtp(profileId, gstin, username, email, otp, firebaseToken())
    }

    fun uploadGstr1(profileId: String, returnPeriod: String, returnDataJson: String) = launchRequest {
        repository.uploadGstr1(profileId, returnPeriod, returnDataJson, firebaseToken())
    }

    fun saveGstr3b(profileId: String, returnPeriod: String, returnDataJson: String) = launchRequest {
        repository.saveGstr3b(profileId, returnPeriod, returnDataJson, firebaseToken())
    }

    fun fileGstr3bWithEvc(
        profileId: String,
        returnPeriod: String,
        pan: String,
        otp: String,
        returnDataJson: String,
    ) = launchRequest {
        repository.fileGstr3bWithEvc(profileId, returnPeriod, pan, otp, returnDataJson, firebaseToken())
    }

    private suspend fun firebaseToken(): String =
        auth.currentUser?.getIdToken(true)?.await()?.token
            ?: throw IllegalStateException("Please sign in before using GST services.")

    private fun launchRequest(block: suspend () -> GstPortalApiResponse) {
        viewModelScope.launch {
            _state.value = GstPortalUiState.Loading
            runCatching { block() }
                .onSuccess { result ->
                    _state.value = if (result.success) GstPortalUiState.Result(result)
                    else GstPortalUiState.Error(result.message)
                }
                .onFailure { error ->
                    _state.value = GstPortalUiState.Error(error.message ?: "GST portal request failed.")
                }
        }
    }
}
