package com.gmaingret.outlinergod.ui.screen.login

import androidx.lifecycle.ViewModel
import com.gmaingret.outlinergod.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.first
import org.orbitmvi.orbit.ContainerHost
import org.orbitmvi.orbit.viewmodel.container
import javax.inject.Inject

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel(), ContainerHost<LoginUiState, LoginSideEffect> {

    override val container = container<LoginUiState, LoginSideEffect>(LoginUiState.CheckingSession)

    fun checkExistingSession() = intent {
        val token = authRepository.getAccessToken().first()
        if (token != null) {
            reduce { LoginUiState.Success }
            postSideEffect(LoginSideEffect.NavigateToDocumentList)
        } else {
            reduce { LoginUiState.Idle }
        }
    }

    fun handleSignInError(message: String) = intent {
        reduce { LoginUiState.Error(message) }
    }

    fun handleGoogleSignIn(idToken: String) = intent {
        if (idToken.isEmpty()) {
            reduce { LoginUiState.Idle }
            return@intent
        }
        reduce { LoginUiState.Loading }
        authRepository.googleSignIn(idToken).fold(
            onSuccess = {
                reduce { LoginUiState.Success }
                postSideEffect(LoginSideEffect.NavigateToDocumentList)
            },
            onFailure = {
                reduce { LoginUiState.Error(it.message ?: "Sign-in failed") }
            }
        )
    }
}

sealed class LoginUiState {
    data object CheckingSession : LoginUiState()
    data object Idle : LoginUiState()
    data object Loading : LoginUiState()
    data object Success : LoginUiState()
    data class Error(val message: String) : LoginUiState()
}

sealed class LoginSideEffect {
    data object NavigateToDocumentList : LoginSideEffect()
}
