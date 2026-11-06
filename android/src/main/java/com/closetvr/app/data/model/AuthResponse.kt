package com.closetvr.app.data.model

data class User(
    val id: Int? = null,
    val name: String? = null,
    val email: String? = null
)

data class AuthResponse(
    val token: String,
    val user: User? = null
)
