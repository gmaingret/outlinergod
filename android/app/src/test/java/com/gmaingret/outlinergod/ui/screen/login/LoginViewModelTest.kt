package com.gmaingret.outlinergod.ui.screen.login

import com.gmaingret.outlinergod.network.model.AuthResponse
import com.gmaingret.outlinergod.network.model.UserProfile
import com.gmaingret.outlinergod.repository.AuthRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.orbitmvi.orbit.test.test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class LoginViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var authRepository: AuthRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        authRepository = mockk()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): LoginViewModel {
        return LoginViewModel(authRepository)
    }

    private fun fakeAuthResponse() = AuthResponse(
        token = "access-token-123",
        refreshToken = "refresh-token-456",
        user = UserProfile(
            id = "uid1",
            googleSub = "gsub1",
            email = "test@test.com",
            name = "Test User",
            picture = "https://pic.example.com"
        ),
        isNewUser = false
    )

    @Test
    fun `initialState is Idle`() = runTest {
        val viewModel = createViewModel()
        viewModel.test(this) {
            // Orbit 10 auto-checks initial state matches LoginUiState.Idle
            // No actions performed — no further states to expect
        }
    }

    @Test
    fun `handleGoogleSignIn sets Loading state immediately`() = runTest {
        coEvery { authRepository.googleSignIn("token") } returns Result.success(fakeAuthResponse())
        val viewModel = createViewModel()
        viewModel.test(this) {
            containerHost.handleGoogleSignIn("token")
            expectState(LoginUiState.Loading)
            expectState(LoginUiState.Success)
            expectSideEffect(LoginSideEffect.NavigateToDocumentList)
        }
    }

    @Test
    fun `handleGoogleSignIn onSuccess transitions to Success and posts NavigateToDocumentList`() = runTest {
        coEvery { authRepository.googleSignIn("token") } returns Result.success(fakeAuthResponse())
        val viewModel = createViewModel()
        viewModel.test(this) {
            containerHost.handleGoogleSignIn("token")
            expectState(LoginUiState.Loading)
            expectState(LoginUiState.Success)
            expectSideEffect(LoginSideEffect.NavigateToDocumentList)
        }
    }

    @Test
    fun `handleGoogleSignIn onFailure sets Error state`() = runTest {
        coEvery { authRepository.googleSignIn(any()) } returns Result.failure(IOException("net error"))
        val viewModel = createViewModel()
        viewModel.test(this) {
            containerHost.handleGoogleSignIn("token")
            expectState(LoginUiState.Loading)
            expectState(LoginUiState.Error("net error"))
        }
    }

    @Test
    fun `handleGoogleSignIn onFailure does not post NavigateToDocumentList side effect`() = runTest {
        coEvery { authRepository.googleSignIn(any()) } returns Result.failure(IOException("net error"))
        val viewModel = createViewModel()
        viewModel.test(this) {
            containerHost.handleGoogleSignIn("token")
            expectState(LoginUiState.Loading)
            expectState(LoginUiState.Error("net error"))
            // No side effect emitted — test completes without expectSideEffect
        }
    }

    @Test
    fun `handleSignInError reduces to Error state`() = runTest {
        val viewModel = createViewModel()
        viewModel.test(this) {
            containerHost.handleSignInError("Sign-in unavailable")
            expectState(LoginUiState.Error("Sign-in unavailable"))
        }
    }

    @Test
    fun `emptyIdToken produces Idle not Error`() = runTest {
        val viewModel = createViewModel()
        viewModel.test(this) {
            containerHost.handleGoogleSignIn("")
            // Empty token is cancellation — reduce back to Idle.
            // Since Idle is already the initial state, Orbit may not emit a duplicate.
            // We verify no Error or Loading was emitted.
        }
    }
}
