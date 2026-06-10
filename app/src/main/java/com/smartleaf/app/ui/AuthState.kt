package com.smartleaf.app.ui

import com.smartleaf.app.data.local.UserEntity

sealed class AuthState {
    object Initializing : AuthState()
    object Idle : AuthState()
    object Loading : AuthState()
    data class Success(val user: UserEntity) : AuthState()
    data class AwaitingVerification(val user: UserEntity, val otp: String) : AuthState()
    data class Error(val message: String) : AuthState()
}
