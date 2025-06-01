package com.example

import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import com.example.plugins.*
import com.example.database.DatabaseConnection

/**
 * Punto de entrada principal de la aplicación
 * Configura y arranca el servidor Ktor en puerto 8080
 */
fun main() {
    println("🚀 Iniciando Dubium API...")

    try {
        // 🗄️ Inicializar base de datos antes de arrancar servidor
        println("📋 Inicializando base de datos...")
        DatabaseConnection.initDatabase()

        // ✅ Verificar conectividad
        if (DatabaseConnection.testConnection()) {
            println("✅ Conexión a base de datos exitosa")
        } else {
            println("Error: No se puede conectar a la base de datos")
            println("Verifica que PostgreSQL esté ejecutándose")
            return
        }

        // 🖥️ Configurar y arrancar servidor
        println("🌐 Arrancando servidor en puerto 8080...")
        embeddedServer(
            factory = Netty,              // Usar Netty como servidor HTTP
            port = 8080,                  // Puerto donde escuchar
            host = "0.0.0.0",            // Escuchar en todas las interfaces
            module = Application::module  // Función de configuración
        ).start(wait = true)             // Bloquear thread principal

    } catch (e: Exception) {
        println("💥 Error fatal arrancando la aplicación:")
        println("   ${e.message}")
        e.printStackTrace()
    }
}

/**
 * Función de configuración principal del módulo Ktor
 * Se llama automáticamente al arrancar el servidor
 * Configura todos los plugins y funcionalidades
 */
fun Application.module() {
    println("⚙️ Configurando módulos de la aplicación...")

    try {
        // 📄 1. Configurar serialización JSON (debe ir primero)
        configureSerialization()
        println("✅ Serialización JSON configurada")

        // 🔐 2. Configurar seguridad (JWT + CORS)
        configureSecurity()
        println("✅ Seguridad (JWT + CORS) configurada")

        // 🛣️ 3. Configurar rutas y endpoints (debe ir último)
        configureRouting()
        println("✅ Rutas de API configuradas")

        // 📊 4. Imprimir resumen de configuración
        printServerInfo()

    } catch (e: Exception) {
        println("💥 Error configurando la aplicación:")
        println("   ${e.message}")
        e.printStackTrace()
        throw e
    }
}

/**
 * Imprimir información del servidor al arrancar
 * Útil para verificar que todo está funcionando
 */
private fun printServerInfo() {
    println("\n" + "=".repeat(50))
    println("🎯 DUBIUM API - SERVIDOR INICIADO")
    println("=".repeat(50))
    println("🌐 URL Base:          http://localhost:8080")
    println("📡 Health Check:     http://localhost:8080/")
    println("🧪 Test Endpoint:    http://localhost:8080/test")
    println("📚 Endpoints disponibles:")
    println("   POST /auth/register   - Registrar usuario")
    println("   POST /auth/login      - Login usuario")
    println("   GET  /auth/verify     - Verificar token (requiere JWT)")
    println("   GET  /user/profile    - Perfil usuario (requiere JWT)")
    println("   POST /auth/logout     - Logout (requiere JWT)")
    println("   GET  /debug/users     - Ver usuarios (solo desarrollo)")
    println("\n📋 Configuración:")
    println("   🗄️ Base de datos:    ${getDatabaseInfo()}")
    println("   🔐 JWT:              Tokens de 30 días")
    println("   🌐 CORS:             Habilitado para desarrollo")
    println("   📄 Serialización:   JSON con kotlinx.serialization")
    println("=".repeat(50))
    println("✅ Servidor listo para recibir peticiones")
    println("=".repeat(50) + "\n")
}

/**
 * Obtener información sobre la conexión de base de datos
 * Para mostrar en el resumen de arranque
 */
private fun getDatabaseInfo(): String {
    return try {
        val userCount = DatabaseConnection.getUserCount()
        "PostgreSQL conectada ($userCount usuarios)"
    } catch (e: Exception) {
        "Error de conexión: ${e.message}"
    }
}

/**
 * Hook de cierre limpio de la aplicación
 * Se ejecuta cuando el servidor se cierra (Ctrl+C, kill, etc.)
 */
fun Application.configureShutdown() {
    // Hook para cierre limpio
    environment.monitor.subscribe(ApplicationStopping) {
        println("\n🛑 Cerrando servidor...")
        println("💾 Limpiando recursos...")

        // Aquí podrías cerrar conexiones, limpiar caches, etc.
        // Por ahora, solo log

        println("✅ Servidor cerrado correctamente")
    }
}

/**
 * Configuración para diferentes entornos
 * Detecta automáticamente si es desarrollo o producción
 */
//fun Application.configureEnvironment() {
//    val isDevelopment = environment.developmentMode
//
//    if (isDevelopment) {
//        println("🔧 Modo: DESARROLLO")
//        println("   - Logging detallado habilitado")
//        println("   - CORS permisivo")
//        println("   - Pretty JSON habilitado")
//    } else {
//        println("🏭 Modo: PRODUCCIÓN")
//        println("   - Logging optimizado")
//        println("   - CORS restrictivo")
//        println("   - JSON compacto")
//    }
//}

/**
 * Health check detallado del sistema
 * Útil para monitoreo y debugging
 */
fun getSystemHealth(): Map<String, Any> {
    return try {
        val dbConnected = DatabaseConnection.testConnection()
        val userCount = if (dbConnected) DatabaseConnection.getUserCount() else 0

        mapOf(
            "status" to if (dbConnected) "healthy" else "degraded",
            "timestamp" to System.currentTimeMillis(),
            "uptime" to getUptimeInfo(),
            "database" to mapOf(
                "connected" to dbConnected,
                "userCount" to userCount
            ),
            "server" to mapOf(
                "port" to 8080,
                "host" to "0.0.0.0"
            ),
            "features" to listOf(
                "JWT Authentication",
                "CORS Enabled",
                "JSON Serialization",
                "PostgreSQL Database"
            )
        )
    } catch (e: Exception) {
        mapOf(
            "status" to "error",
            "timestamp" to System.currentTimeMillis(),
            "error" to (e.message ?: "Error desconocido")
        )
    }
}

/**
 * Información de uptime del servidor
 */
private fun getUptimeInfo(): Map<String, Any> {
    val runtime = Runtime.getRuntime()
    return mapOf(
        "totalMemoryMB" to runtime.totalMemory() / (1024 * 1024),
        "freeMemoryMB" to runtime.freeMemory() / (1024 * 1024),
        "maxMemoryMB" to runtime.maxMemory() / (1024 * 1024),
        "availableProcessors" to runtime.availableProcessors()
    )
}

/**
 * Función de desarrollo para crear datos de prueba
 * ⚠️ Solo ejecutar en desarrollo
 */
fun createTestData() {
    try {
        println("🧪 Creando datos de prueba...")

        // Crear usuario de prueba si no existe
        if (!DatabaseConnection.userExists("testuser")) {
            DatabaseConnection.insertUser(
                "testuser",
                // Password: "test123" hasheado
                "\$2a\$12\$CwTycUXWue0Thq9StjUM0uOzL6UtfXX0j8lVyHlSzl8MZYZ5j8i9S"
            )
            println("✅ Usuario de prueba creado: testuser / test123")
        } else {
            println("ℹ️ Usuario de prueba ya existe")
        }

    } catch (e: Exception) {
        println("❌ Error creando datos de prueba: ${e.message}")
    }
}

/**
 * Función para limpiar datos de desarrollo
 * ⚠️ PELIGROSO - Solo usar en desarrollo
 */
fun clearDevelopmentData() {
    try {
        println("🗑️ Limpiando datos de desarrollo...")
        DatabaseConnection.clearAllUsers()
        println("✅ Datos limpiados")
    } catch (e: Exception) {
        println("❌ Error limpiando datos: ${e.message}")
    }
}