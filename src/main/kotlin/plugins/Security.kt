package com.example.plugins

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.http.*
import io.ktor.server.response.*
import java.util.*

/**
 * Configuración de seguridad para la API
 * Incluye JWT authentication y CORS para conexión con Android
 */
fun Application.configureSecurity() {

    // 🌐 CONFIGURACIÓN CORS - Para permitir requests desde Android
    install(CORS) {
        // Métodos HTTP permitidos
        allowMethod(HttpMethod.Options)
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Delete)
        allowMethod(HttpMethod.Patch)

        // Headers permitidos
        allowHeader(HttpHeaders.Authorization)     // Para JWT tokens
        allowHeader(HttpHeaders.ContentType)      // Para JSON
        allowHeader(HttpHeaders.Accept)           // Para response types
        allowHeader("X-Requested-With")          // Para frameworks JS

        // Permitir credentials (cookies, auth headers)
        allowCredentials = true

        // Hosts permitidos (en desarrollo permitir todo)
        anyHost() // ⚠️ En producción cambiar por dominios específicos

        // En producción usar:
        // allowHost("tu-dominio.com")
        // allowHost("tu-app-android", schemes = listOf("http", "https"))
    }

    // 🔐 CONFIGURACIÓN JWT
    val jwtConfig = JWTConfig()

    install(Authentication) {
        jwt("auth-jwt") {
            realm = jwtConfig.realm

            // Configurar verificador de tokens
            verifier(
                JWT.require(Algorithm.HMAC256(jwtConfig.secret))
                    .withAudience(jwtConfig.audience)
                    .withIssuer(jwtConfig.issuer)
                    .build()
            )

            // Validar el token y extraer información
            validate { credential ->
                try {
                    // Verificar que el token tiene los claims necesarios
                    val username = credential.payload.getClaim("username").asString()
                    val userId = credential.payload.getClaim("userId").asInt()

                    if (username != null && userId != null && userId > 0) {
                        // Token válido - crear principal
                        JWTPrincipal(credential.payload)
                    } else {
                        // Token inválido - faltan claims
                        null
                    }
                } catch (e: Exception) {
                    // Error procesando token - considerar inválido
                    null
                }
            }

            // Configurar response cuando token es inválido
            challenge { defaultScheme, realm ->         //FIXEADO
                call.respondText(
                    "Token inválido o expirado",
                    status = HttpStatusCode.Unauthorized
                )
            }
        }
    }
}

/**
 * Configuración centralizada para JWT
 * Facilita cambiar configuración en un solo lugar
 */
private class JWTConfig {
    // 🔑 Clave secreta para firmar tokens
    // ⚠️ En producción usar variable de entorno
    val secret = "dubium-secret-key-2024-super-secure"

    // 🏢 Emisor del token (tu aplicación)
    val issuer = "dubium-api"

    // 👥 Audiencia del token (quién puede usarlo)
    val audience = "dubium-users"

    // 🏰 Realm para auth challenge
    val realm = "dubium-jwt-realm"

    // ⏰ Duración del token (30 días en milisegundos)
    val expirationTime = 30L * 24L * 60L * 60L * 1000L // 30 días
}

/**
 * Generar un nuevo token JWT para un usuario
 * @param userId - ID del usuario en la base de datos
 * @param username - nombre de usuario
 * @return String - token JWT listo para enviar al cliente
 */
fun generateToken(userId: Int, username: String): String {
    val config = JWTConfig()

    return JWT.create()
        .withAudience(config.audience)                    // Para quién es el token
        .withIssuer(config.issuer)                        // Quién lo emite
        .withClaim("userId", userId)                      // ID del usuario
        .withClaim("username", username)                  // Username del usuario
        .withIssuedAt(Date())                             // Cuándo se creó
        .withExpiresAt(Date(System.currentTimeMillis() + config.expirationTime)) // Cuándo expira (30 días)
        .sign(Algorithm.HMAC256(config.secret))           // Firmar con clave secreta
}

/**
 * Extraer información del usuario desde un JWT token válido
 * Utilidad para usar en endpoints protegidos
 * @param principal - JWTPrincipal obtenido del middleware de auth
 * @return UserTokenInfo - información del usuario extraída del token
 */
fun extractUserFromToken(principal: JWTPrincipal): UserTokenInfo {
    return UserTokenInfo(
        userId = principal.payload.getClaim("userId").asInt(),
        username = principal.payload.getClaim("username").asString(),
        issuedAt = principal.payload.issuedAt,
        expiresAt = principal.payload.expiresAt
    )
}

/**
 * Data class con información del usuario extraída del token
 */
data class UserTokenInfo(
    val userId: Int,
    val username: String,
    val issuedAt: Date?,
    val expiresAt: Date?
) {
    /**
     * Verificar si el token está cerca de expirar
     * @param hoursBeforeExpiry - horas antes de expiración para considerar "cerca"
     * @return Boolean - true si expira pronto
     */
    fun isExpiringSoon(hoursBeforeExpiry: Long = 24): Boolean {
        val now = Date()
        val expiryThreshold = Date(now.time + (hoursBeforeExpiry * 60 * 60 * 1000))
        return expiresAt?.before(expiryThreshold) ?: false
    }

    /**
     * Obtener tiempo restante hasta expiración en horas
     * @return Long - horas hasta expiración (puede ser negativo si ya expiró)
     */
    fun hoursUntilExpiry(): Long {
        val now = Date()
        return if (expiresAt != null) {
            (expiresAt.time - now.time) / (60 * 60 * 1000)
        } else {
            0L
        }
    }
}

/**
 * Validar manualmente un token JWT (sin middleware)
 * Útil para casos especiales o debugging
 * @param token - token JWT como string
 * @return UserTokenInfo? - información del usuario o null si token inválido
 */
fun validateTokenManually(token: String): UserTokenInfo? {
    return try {
        val config = JWTConfig()

        val verifier = JWT.require(Algorithm.HMAC256(config.secret))
            .withAudience(config.audience)
            .withIssuer(config.issuer)
            .build()

        val decodedJWT = verifier.verify(token)

        UserTokenInfo(
            userId = decodedJWT.getClaim("userId").asInt(),
            username = decodedJWT.getClaim("username").asString(),
            issuedAt = decodedJWT.issuedAt,
            expiresAt = decodedJWT.expiresAt
        )

    } catch (e: Exception) {
        null
    }
}

/**
 * Generar un token de prueba para testing
 * ⚠️ Solo usar en desarrollo
 */
fun generateTestToken(userId: Int = 1, username: String = "testuser"): String {
    return generateToken(userId, username)
}

/**
 * Información sobre la configuración JWT actual
 * Útil para debugging y health checks
 */
fun getJWTInfo(): Map<String, Any> {
    val config = JWTConfig()
    return mapOf(
        "issuer" to config.issuer,
        "audience" to config.audience,
        "realm" to config.realm,
        "expirationDays" to (config.expirationTime / (24 * 60 * 60 * 1000)), // Convertir a días
        "algorithm" to "HMAC256"
    )
}