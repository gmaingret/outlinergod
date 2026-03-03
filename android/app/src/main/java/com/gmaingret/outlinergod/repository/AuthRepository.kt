package com.gmaingret.outlinergod.repository

import com.gmaingret.outlinergod.network.model.AuthResponse
import com.gmaingret.outlinergod.network.model.TokenPair
import com.gmaingret.outlinergod.network.model.UserProfile
import kotlinx.coroutines.flow.Flow

interface AuthRepository {
    suspend fun googleSignIn(idToken: String): Result<AuthResponse>
    suspend fun refreshToken(): Result<TokenPair>
    suspend fun getMe(): Result<UserProfile>
    suspend fun logout(refreshToken: String): Result<Unit>
    fun getAccessToken(): Flow<String?>
    fun getUserId(): Flow<String?>
    fun getDeviceId(): Flow<String>
}
