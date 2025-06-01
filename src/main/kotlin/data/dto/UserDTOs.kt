package com.example.data.dto

import kotlinx.serialization.Serializable

// 📥 REQUESTS - Lo que recibe la API desde Android

/**
 * Datos que envía Android para hacer login
 */
@Serializable
data class LoginRequest(
    val username: String,
    val password: String
)

/**
 * Datos que envía Android para registrarse
 */
@Serializable
data class RegisterRequest(
    val username: String,
    val password: String
)

// 📤 RESPONSES - Lo que envía la API hacia Android

/**
 * Información del usuario (sin datos sensibles)
 * Se usa en responses de login, registro, perfil, etc.
 */
@Serializable
data class UserDto(
    val id: Int,
    val username: String,
    val createdAt: String // Formato: "2024-05-31T10:30:00"
)

/**
 * Respuesta de autenticación (login/register exitoso)
 * Contiene el token JWT y datos del usuario
 */
@Serializable
data class AuthResponse(
    val success: Boolean,
    val token: String? = null,        // Token JWT para futuras peticiones
    val user: UserDto? = null,        // Datos del usuario logueado
    val message: String? = null       // Mensaje descriptivo (ej: "Login exitoso")
)

/**
 * Respuesta genérica de éxito
 * Para endpoints que devuelven datos pero no son de auth
 */
@Serializable
data class ApiResponse<T>(
    val success: Boolean,
    val data: T? = null,              // Datos específicos del endpoint
    val message: String? = null       // Mensaje descriptivo opcional
)

/**
 * Respuesta de error estándar
 * Se usa para todos los errores (400, 401, 404, 500, etc.)
 */
@Serializable
data class ErrorResponse(
    val success: Boolean = false,     // Siempre false para errores
    val error: String,                // Mensaje de error principal
    val message: String? = null       // Detalles adicionales del error
)

/**
 * DTO interno - Usuario con contraseña hasheada
 * NO se envía nunca a Android, solo se usa internamente en el servidor
 * Para transferir datos entre UserService y DatabaseConnection
 */
data class UserWithPassword(
    val id: Int,
    val username: String,
    val passwordHash: String,         // Contraseña hasheada con BCrypt
    val createdAt: String
)

/**
 * Respuesta de verificación de token
 * Para el endpoint /auth/verify
 */
@Serializable
data class TokenVerificationResponse(
    val success: Boolean,
    val valid: Boolean,               // Si el token es válido
    val user: UserDto? = null,        // Datos del usuario si token es válido
    val message: String? = null
)

/**
 * Respuesta de logout
 * Para el endpoint /auth/logout
 */
@Serializable
data class LogoutResponse(
    val success: Boolean,
    val message: String
)