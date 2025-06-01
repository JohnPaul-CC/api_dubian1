package com.example.plugins

import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*
import kotlinx.serialization.json.Json

/**
 * Configuración de serialización JSON para la API
 * Maneja la conversión automática entre objetos Kotlin ↔ JSON
 */
fun Application.configureSerialization() {

    install(ContentNegotiation) {

        // 📄 Configurar JSON como formato principal
        json(Json {

            // 🎨 Pretty printing - JSON formateado y legible
            // En desarrollo: true para debugging fácil
            // En producción: false para menor tamaño
            prettyPrint = true

            // 🔧 Ser flexible con JSON de entrada
            // Permite JSON con formato relajado (comentarios, trailing commas, etc.)
            isLenient = true

            // 🚫 Ignorar campos desconocidos
            // Si Android envía campos extra que no están en el DTO, no fallar
            ignoreUnknownKeys = true

            // 📝 Usar nombres de propiedades por defecto
            // No transformar camelCase ↔ snake_case automáticamente
            useAlternativeNames = false

            // 🔤 Codificación de caracteres especiales
            // Permitir caracteres Unicode sin escapar
            encodeDefaults = true

            // ⚡ Modo de serialización de clases selladas
            // Usar discriminator por defecto para polimorfismo
            useArrayPolymorphism = false

            // 🛡️ Permitir valores null explícitos en JSON
            // Serializar campos null como "field": null en lugar de omitirlos
            explicitNulls = true

            // 🎯 Estrategia para enum classes
            // Usar nombre del enum en lugar de ordinal
            // classDiscriminator = "type"
        })

        // 📋 Configuraciones adicionales si necesitas otros formatos
        // xml() // Para XML si lo necesitas en el futuro
        // cbor() // Para datos binarios compactos
    }
}


/**
 * Extensión para serializar objetos manualmente
 * Útil para logging o debugging
 */
fun Any.toJsonString(prettyPrint: Boolean = true): String {
    val json = Json {
        this.prettyPrint = prettyPrint
        ignoreUnknownKeys = true
        isLenient = true
    }
    return json.encodeToString(kotlinx.serialization.serializer(), this)
}

/**
 * Extensión para deserializar JSON manualmente
 * Útil para procesar datos externos
 */
inline fun <reified T> String.fromJsonString(): T {
    val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }
    return json.decodeFromString<T>(this)
}

/**
 * Configuración específica para APIs RESTful
 * Optimizada para comunicación móvil
 */
fun Application.configureRestApiSerialization() {
    install(ContentNegotiation) {
        json(Json {
            // 📱 Optimizado para apps móviles
            prettyPrint = false              // Menor tamaño de respuesta
            ignoreUnknownKeys = true         // Flexibilidad en versioning de API
            isLenient = false               // Estricto para detectar errores del cliente
            encodeDefaults = false          // Omitir campos con valores por defecto
            explicitNulls = false           // Omitir campos null para menor tamaño

            // 🚀 Performance optimizations
            useAlternativeNames = false     // No transformaciones de nombres
            useArrayPolymorphism = false    // Serialización más simple
        })
    }
}

/**
 * Validar que la serialización está funcionando correctamente
 * Útil para health checks
 */
fun testSerialization(): Map<String, Any> {
    return try {
        // Test object para serializar
        val testObject = mapOf(
            "message" to "Serialization test",
            "timestamp" to System.currentTimeMillis(),
            "success" to true,
            "data" to listOf("item1", "item2", "item3"),
            "nested" to mapOf(
                "key1" to "value1",
                "key2" to 42
            )
        )

        // Convertir a JSON y de vuelta
        val jsonString = testObject.toJsonString()
        val backToObject = jsonString.fromJsonString<Map<String, Any>>()

        mapOf(
            "success" to true,
            "message" to "Serialización funcionando correctamente",
            "originalSize" to testObject.size,
            "serializedLength" to jsonString.length,
            "roundTripSuccess" to (testObject.keys == backToObject.keys)
        )

    } catch (e: Exception) {
        mapOf(
            "success" to false,
            "message" to "Error en serialización",
            "error" to (e.message ?: "Error desconocido")
        )
    }
}

/**
 * Información sobre la configuración actual de serialización
 * Para debugging y monitoreo
 */
fun getSerializationInfo(): Map<String, Any> {
    return mapOf(
        "format" to "JSON",
        "library" to "kotlinx.serialization",
        "features" to listOf(
            "Pretty printing configurable",
            "Lenient parsing",
            "Ignore unknown keys",
            "Null handling configurable",
            "Unicode support"
        ),
        "contentTypes" to listOf(
            "application/json",
            "text/json"
        ),
        "encoding" to "UTF-8"
    )
}