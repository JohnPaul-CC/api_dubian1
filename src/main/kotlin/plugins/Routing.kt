package com.example.plugins

import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.http.*
import com.example.service.UserService
import com.example.data.dto.*

fun Application.configureRouting() {
    val userService = UserService()

    routing {
        // 🏥 Health check - verificar que API funciona
        get("/") {
            call.respondText("🚀 Dubium API funcionando correctamente!")
        }

        // 🧪 Test endpoint - para probar conexión desde Android
        get("/test") {
            call.respond(mapOf(
                "status" to "OK",
                "message" to "API conectada",
                "timestamp" to System.currentTimeMillis()
            ))
        }

        // 🔐 ENDPOINTS PÚBLICOS (no requieren token)

        // ✍️ Registro de usuario
        post("/auth/register") {
            try {
                val request = call.receive<RegisterRequest>()

                // Validaciones básicas de formato
                if (request.username.isBlank()) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse(
                        error = "Username requerido"
                    ))
                    return@post
                }

                if (request.password.isBlank()) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse(
                        error = "Password requerido"
                    ))
                    return@post
                }

                // Delegar lógica al UserService
                val result = userService.registerUser(request.username, request.password)

                result.fold(
                    onSuccess = { user ->
                        // Generar token JWT de 30 días
                        val token = generateToken(user.id, user.username)

                        call.respond(HttpStatusCode.Created, AuthResponse(
                            success = true,
                            token = token,
                            user = user,
                            message = "Usuario registrado exitosamente"
                        ))
                    },
                    onFailure = { error ->
                        call.respond(HttpStatusCode.BadRequest, ErrorResponse(
                            error = error.message ?: "Error desconocido"
                        ))
                    }
                )

            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, ErrorResponse(
                    error = "Error interno del servidor",
                    message = e.message
                ))
            }
        }

        // 🔑 Login de usuario
        post("/auth/login") {
            try {
                val request = call.receive<LoginRequest>()

                // Validaciones básicas
                if (request.username.isBlank() || request.password.isBlank()) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse(
                        error = "Username y password requeridos"
                    ))
                    return@post
                }

                // Delegar al UserService
                val result = userService.loginUser(request.username, request.password)

                result.fold(
                    onSuccess = { user ->
                        // Generar token JWT de 30 días
                        val token = generateToken(user.id, user.username)

                        call.respond(AuthResponse(
                            success = true,
                            token = token,
                            user = user,
                            message = "Login exitoso"
                        ))
                    },
                    onFailure = { error ->
                        // Determinar tipo de error para respuesta apropiada
                        val statusCode = when {
                            error.message?.contains("no encontrado") == true -> HttpStatusCode.NotFound
                            error.message?.contains("contraseña") == true -> HttpStatusCode.Unauthorized
                            else -> HttpStatusCode.BadRequest
                        }

                        call.respond(statusCode, ErrorResponse(
                            error = error.message ?: "Error de autenticación"
                        ))
                    }
                )

            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, ErrorResponse(
                    error = "Error interno del servidor",
                    message = e.message
                ))
            }
        }

        // 🔒 ENDPOINTS PROTEGIDOS (requieren JWT token válido)
        authenticate("auth-jwt") {

            // ✅ Verificar si token es válido (para auto-login)
            get("/auth/verify") {
                try {
                    val principal = call.principal<JWTPrincipal>()
                    val username = principal!!.payload.getClaim("username").asString()
                    val userId = principal.payload.getClaim("userId").asInt()

                    // Opcional: verificar que el usuario aún existe en BD
                    val result = userService.getUserById(userId)

                    result.fold(
                        onSuccess = { user ->
                            call.respond(mapOf(
                                "success" to true,
                                "valid" to true,
                                "user" to user,
                                "message" to "Token válido"
                            ))
                        },
                        onFailure = {
                            // Usuario fue eliminado pero token aún es válido
                            call.respond(HttpStatusCode.Unauthorized, ErrorResponse(
                                error = "Usuario no válido"
                            ))
                        }
                    )

                } catch (e: Exception) {
                    call.respond(HttpStatusCode.Unauthorized, ErrorResponse(
                        error = "Token inválido",
                        message = e.message
                    ))
                }
            }

            // 👤 Obtener perfil del usuario actual
            get("/user/profile") {
                try {
                    val principal = call.principal<JWTPrincipal>()
                    val userId = principal!!.payload.getClaim("userId").asInt()

                    val result = userService.getUserById(userId)

                    result.fold(
                        onSuccess = { user ->
                            call.respond(mapOf(
                                "success" to true,
                                "data" to user
                            ))
                        },
                        onFailure = { error ->
                            call.respond(HttpStatusCode.NotFound, ErrorResponse(
                                error = error.message ?: "Usuario no encontrado"
                            ))
                        }
                    )

                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, ErrorResponse(
                        error = "Error obteniendo perfil",
                        message = e.message
                    ))
                }
            }

            // 🚪 Logout (opcional - invalida token del lado del cliente)
            post("/auth/logout") {
                try {
                    // En JWT stateless, el "logout" es principalmente del lado del cliente
                    // El cliente debe borrar el token
                    // Aquí podríamos agregar el token a una "blacklist" si fuera necesario

                    call.respond(mapOf(
                        "success" to true,
                        "message" to "Logout exitoso. Borra el token del dispositivo."
                    ))

                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, ErrorResponse(
                        error = "Error en logout",
                        message = e.message
                    ))
                }
            }
        }

        // 🔍 Endpoint para debugging (solo en desarrollo)
        get("/debug/users") {
            try {
                // Solo habilitar en desarrollo
                val isDevelopment = environment.developmentMode
                if (!isDevelopment) {
                    call.respond(HttpStatusCode.NotFound, ErrorResponse(
                        error = "Endpoint no disponible"
                    ))
                    return@get
                }

                val users = userService.getAllUsers()
                call.respond(mapOf(
                    "success" to true,
                    "users" to users,
                    "count" to users.size
                ))

            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, ErrorResponse(
                    error = "Error obteniendo usuarios",
                    message = e.message
                ))
            }
        }
    }
}