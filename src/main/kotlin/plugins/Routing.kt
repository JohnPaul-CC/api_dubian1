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
            val response = TestResponse(
                status = "OK",
                message = "API conectada",
                timestamp = System.currentTimeMillis()
            )
            call.respond(HttpStatusCode.OK, response)
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
                    println("📡 Server: Request a /auth/verify recibido") // Debug

                    val principal = call.principal<JWTPrincipal>()
                    val username = principal!!.payload.getClaim("username").asString()
                    val userId = principal.payload.getClaim("userId").asInt()

                    println("👤 Server: Usuario del token: $username (ID: $userId)") // Debug

                    // Opcional: verificar que el usuario aún existe en BD
                    val result = userService.getUserById(userId)

                    result.fold(
                        onSuccess = { user ->
                            println("✅ Server: Token verificado exitosamente") // Debug
                            // ✅ USAR TokenVerificationResponse (ya está definido)
                            call.respond(TokenVerificationResponse(
                                success = true,
                                valid = true,
                                user = user,
                                message = "Token válido"
                            ))
                        },
                        onFailure = {
                            println("❌ Server: Usuario no válido") // Debug
                            call.respond(HttpStatusCode.Unauthorized, ErrorResponse(
                                error = "Usuario no válido"
                            ))
                        }
                    )

                } catch (e: Exception) {
                    println("💥 Server: Error en /auth/verify: ${e.message}") // Debug
                    call.respond(HttpStatusCode.Unauthorized, ErrorResponse(
                        error = "Token inválido",
                        message = e.message
                    ))
                }
            }

            // 👤 Obtener perfil del usuario actual
            get("/user/profile") {
                try {
                    println("📡 Server: Request a /user/profile recibido")

                    val authHeader = call.request.headers["Authorization"]
                    println("🔑 Server: Auth header: $authHeader")

                    val principal = call.principal<JWTPrincipal>()
                    println("👤 Server: Principal obtenido: $principal")

                    val userId = principal!!.payload.getClaim("userId").asInt()
                    println("🆔 Server: User ID del token: $userId")

                    val result = userService.getUserById(userId)

                    result.fold(
                        onSuccess = { user ->
                            println("✅ Server: Usuario encontrado: $user")
                            // ✅ CAMBIAR ESTA LÍNEA:
                            call.respond(ApiResponse(
                                success = true,
                                data = user
                            ))
                        },
                        onFailure = { error ->
                            println("❌ Server: Error buscando usuario: ${error.message}")
                            call.respond(HttpStatusCode.NotFound, ErrorResponse(
                                error = error.message ?: "Usuario no encontrado"
                            ))
                        }
                    )

                } catch (e: Exception) {
                    println("💥 Server: Excepción en /user/profile: ${e.message}")
                    e.printStackTrace()
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
                val users = userService.getAllUsers()

                val response = DebugUsersResponse(
                    success = true,
                    count = users.size,
                    users = users
                )

                call.respond(HttpStatusCode.OK, response)

            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, ErrorResponse(
                    error = "Error obteniendo usuarios",
                    message = e.message
                ))
            }
        }
    }
}