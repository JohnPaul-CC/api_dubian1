package com.example.service

import com.example.database.DatabaseConnection
import com.example.data.dto.UserDto
import org.mindrot.jbcrypt.BCrypt
import java.sql.SQLException
import java.time.format.DateTimeFormatter

/**
 * Servicio que maneja toda la lógica de negocio relacionada con usuarios
 * Actúa como capa intermedia entre Routing (HTTP) y DatabaseConnection (BD)
 */
class UserService {

    // 🔧 CONFIGURACIÓN Y VALIDACIONES

    companion object {
        private const val MIN_USERNAME_LENGTH = 3
        private const val MAX_USERNAME_LENGTH = 50
        private const val MIN_PASSWORD_LENGTH = 4
        private const val MAX_PASSWORD_LENGTH = 100

        // Regex para username válido (letras, números, guión bajo)
        private val USERNAME_REGEX = "^[a-zA-Z0-9_]+$".toRegex()
    }

    // 📝 REGISTRO DE USUARIOS

    /**
     * Registrar un nuevo usuario en el sistema
     * @param username - nombre de usuario deseado
     * @param password - contraseña en texto plano
     * @return Result<UserDto> - éxito con datos del usuario o error
     */
    fun registerUser(username: String, password: String): Result<UserDto> {
        return try {
            // 1. Validar datos de entrada
            validateUsername(username).getOrElse {
                return Result.failure(Exception(it))
            }

            validatePassword(password).getOrElse {
                return Result.failure(Exception(it))
            }

            // 2. Verificar que el username no exista
            if (DatabaseConnection.userExists(username)) {
                return Result.failure(Exception("El username '$username' ya está en uso"))
            }

            // 3. Hashear la contraseña
            val hashedPassword = hashPassword(password)

            // 4. Insertar en base de datos
            val userData = DatabaseConnection.insertUser(username, hashedPassword)

            // 5. Convertir a DTO (sin datos sensibles)
            val userDto = UserDto(
                id = userData.id,
                username = userData.username,
                createdAt = userData.createdAt.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
            )

            Result.success(userDto)

        } catch (e: SQLException) {
            // Manejar errores específicos de base de datos
            val errorMessage = when {
                e.message?.contains("duplicate key") == true ->
                    "El username ya existe"
                e.message?.contains("connection") == true ->
                    "Error de conexión a la base de datos"
                else ->
                    "Error interno al registrar usuario"
            }
            Result.failure(Exception(errorMessage))

        } catch (e: Exception) {
            Result.failure(Exception("Error inesperado al registrar usuario: ${e.message}"))
        }
    }

    // 🔑 AUTENTICACIÓN

    /**
     * Autenticar usuario con username y contraseña
     * @param username - nombre de usuario
     * @param password - contraseña en texto plano
     * @return Result<UserDto> - éxito con datos del usuario o error
     */
    fun loginUser(username: String, password: String): Result<UserDto> {
        return try {
            // 1. Validaciones básicas
            if (username.isBlank()) {
                return Result.failure(Exception("Username requerido"))
            }

            if (password.isBlank()) {
                return Result.failure(Exception("Password requerido"))
            }

            // 2. Buscar usuario en base de datos
            val userData = DatabaseConnection.findUserByUsername(username)
                ?: return Result.failure(Exception("Usuario no encontrado"))

            // 3. Verificar contraseña
            if (!verifyPassword(password, userData.passwordHash)) {
                return Result.failure(Exception("Contraseña incorrecta"))
            }

            // 4. Login exitoso - convertir a DTO
            val userDto = UserDto(
                id = userData.id,
                username = userData.username,
                createdAt = userData.createdAt.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
            )

            Result.success(userDto)

        } catch (e: SQLException) {
            Result.failure(Exception("Error de base de datos en login"))

        } catch (e: Exception) {
            Result.failure(Exception("Error inesperado en login: ${e.message}"))
        }
    }

    // 👤 GESTIÓN DE USUARIOS

    /**
     * Obtener datos de un usuario por su ID
     * @param userId - ID del usuario a buscar
     * @return Result<UserDto> - datos del usuario o error si no existe
     */
    fun getUserById(userId: Int): Result<UserDto> {
        return try {
            // Validar ID válido
            if (userId <= 0) {
                return Result.failure(Exception("ID de usuario inválido"))
            }

            // Buscar en base de datos
            val userData = DatabaseConnection.findUserById(userId)
                ?: return Result.failure(Exception("Usuario no encontrado"))

            // Convertir a DTO
            val userDto = UserDto(
                id = userData.id,
                username = userData.username,
                createdAt = userData.createdAt.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
            )

            Result.success(userDto)

        } catch (e: SQLException) {
            Result.failure(Exception("Error de base de datos"))

        } catch (e: Exception) {
            Result.failure(Exception("Error obteniendo usuario: ${e.message}"))
        }
    }

    /**
     * Obtener lista de todos los usuarios (solo para desarrollo/admin)
     * @return List<UserDto> - lista de usuarios sin datos sensibles
     */
    fun getAllUsers(): List<UserDto> {
        return try {
            DatabaseConnection.getAllUsers().map { userData ->
                UserDto(
                    id = userData.id,
                    username = userData.username,
                    createdAt = userData.createdAt.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Verificar si un usuario existe por username
     * @param username - username a verificar
     * @return Boolean - true si existe, false si no
     */
    fun userExists(username: String): Boolean {
        return try {
            DatabaseConnection.userExists(username)
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Obtener estadísticas básicas de usuarios
     * @return Map<String, Any> - estadísticas del sistema
     */
    fun getUserStats(): Map<String, Any> {
        return try {
            mapOf(
                "totalUsers" to DatabaseConnection.getUserCount(),
                "databaseConnected" to DatabaseConnection.testConnection()
            )
        } catch (e: Exception) {
            mapOf(
                "totalUsers" to 0,
                "databaseConnected" to false,
                "error" to (e.message ?: "Error desconocido")  // ← FIX
            )
        }
    }

    // 🔒 VALIDACIONES PRIVADAS

    /**
     * Validar formato y longitud del username
     */
    private fun validateUsername(username: String): Result<String> {
        return when {
            username.isBlank() ->
                Result.failure(Exception("Username requerido"))

            username.length < MIN_USERNAME_LENGTH ->
                Result.failure(Exception("Username debe tener al menos $MIN_USERNAME_LENGTH caracteres"))

            username.length > MAX_USERNAME_LENGTH ->
                Result.failure(Exception("Username no puede tener más de $MAX_USERNAME_LENGTH caracteres"))

            !username.matches(USERNAME_REGEX) ->
                Result.failure(Exception("Username solo puede contener letras, números y guión bajo"))

            else -> Result.success(username)
        }
    }

    /**
     * Validar formato y longitud de la contraseña
     */
    private fun validatePassword(password: String): Result<String> {
        return when {
            password.isBlank() ->
                Result.failure(Exception("Contraseña requerida"))

            password.length < MIN_PASSWORD_LENGTH ->
                Result.failure(Exception("Contraseña debe tener al menos $MIN_PASSWORD_LENGTH caracteres"))

            password.length > MAX_PASSWORD_LENGTH ->
                Result.failure(Exception("Contraseña no puede tener más de $MAX_PASSWORD_LENGTH caracteres"))

            password.contains(" ") ->
                Result.failure(Exception("Contraseña no puede contener espacios"))

            else -> Result.success(password)
        }
    }

    // 🔐 MANEJO DE CONTRASEÑAS

    /**
     * Hashear contraseña usando BCrypt
     * @param password - contraseña en texto plano
     * @return String - hash de la contraseña
     */
    private fun hashPassword(password: String): String {
        return BCrypt.hashpw(password, BCrypt.gensalt(12)) // 12 rounds para seguridad
    }

    /**
     * Verificar si una contraseña coincide con su hash
     * @param password - contraseña en texto plano
     * @param hash - hash almacenado en base de datos
     * @return Boolean - true si coincide, false si no
     */
    private fun verifyPassword(password: String, hash: String): Boolean {
        return try {
            BCrypt.checkpw(password, hash)
        } catch (e: Exception) {
            false // Si hay cualquier error, considerar como contraseña incorrecta
        }
    }

    // 🧪 UTILIDADES PARA DESARROLLO

    /**
     * Crear usuario de prueba (solo para desarrollo)
     * ⚠️ No usar en producción
     */
    fun createTestUser(username: String = "testuser", password: String = "test123"): Result<UserDto> {
        return registerUser(username, password)
    }

    /**
     * Limpiar todos los usuarios (solo para testing)
     * ⚠️ PELIGROSO - Solo usar en desarrollo
     */
    fun clearAllUsers() {
        try {
            DatabaseConnection.clearAllUsers()
        } catch (e: Exception) {
            println("Error limpiando usuarios: ${e.message}")
        }
    }
}