package com.gmaingret.outlinergod.network.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AuthResponse(
    @SerialName("token") val token: String,
    @SerialName("refresh_token") val refreshToken: String,
    @SerialName("user") val user: UserProfile,
    @SerialName("is_new_user") val isNewUser: Boolean
)

@Serializable
data class TokenPair(
    @SerialName("token") val token: String,
    @SerialName("refresh_token") val refreshToken: String
)

@Serializable
data class UserProfile(
    @SerialName("id") val id: String,
    @SerialName("google_sub") val googleSub: String,
    @SerialName("email") val email: String,
    @SerialName("name") val name: String,
    @SerialName("picture") val picture: String
)
