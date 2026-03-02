package com.gmaingret.outlinergod.ui.screen.login

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import androidx.hilt.navigation.compose.hiltViewModel
import com.gmaingret.outlinergod.BuildConfig
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential

@Composable
fun LoginScreen(
    onNavigateToDocumentList: () -> Unit,
    viewModel: LoginViewModel = hiltViewModel()
) {
    val state by viewModel.container.stateFlow.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    // Collect side effects for navigation
    LaunchedEffect(Unit) {
        viewModel.container.sideEffectFlow.collect { sideEffect ->
            when (sideEffect) {
                is LoginSideEffect.NavigateToDocumentList -> onNavigateToDocumentList()
            }
        }
    }

    // Launch Google Sign-In via Credential Manager
    LaunchedEffect(Unit) {
        val credentialManager = CredentialManager.create(context)
        val googleIdOption = GetGoogleIdOption.Builder()
            .setServerClientId(BuildConfig.GOOGLE_CLIENT_ID)
            .setFilterByAuthorizedAccounts(false)
            .build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()
        try {
            val result = credentialManager.getCredential(context, request)
            val idToken = GoogleIdTokenCredential.createFrom(result.credential.data).idToken
            viewModel.handleGoogleSignIn(idToken)
        } catch (e: GetCredentialException) {
            viewModel.handleGoogleSignIn("")
        }
    }

    // Show error in snackbar
    LaunchedEffect(state) {
        if (state is LoginUiState.Error) {
            snackbarHostState.showSnackbar((state as LoginUiState.Error).message)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "OutlinerGod",
                    style = MaterialTheme.typography.headlineLarge
                )

                Spacer(modifier = Modifier.height(32.dp))

                when (state) {
                    is LoginUiState.Loading -> {
                        CircularProgressIndicator()
                    }
                    is LoginUiState.Error -> {
                        Text(
                            text = "Sign-in failed",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = {
                            // Re-trigger sign-in by recomposing — user can tap retry
                        }) {
                            Text("Retry")
                        }
                    }
                    is LoginUiState.Idle -> {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Sign in with Google",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    is LoginUiState.Success -> {
                        // Navigation side effect handles transition
                        Text(
                            text = "Signed in",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }
    }
}
