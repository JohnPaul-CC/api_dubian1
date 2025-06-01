package com.example.database

import java.sql.Connection
import java.sql.DriverManager
import java.sql.ResultSet
import java.sql.SQLException
import java.sql.Timestamp
import java.time.LocalDateTime

/**
 * Maneja todas las operaciones de base de datos con PostgreSQL
 * Conexión directa usando JDBC sin ORM
 */
object DatabaseConnection {

    // 🔧 CONFIGURACIÓN DE BASE DE DATOS
    // TODO: Cambiar estos valores por tu configuración real
    private const val DB_URL = "jdbc:postgresql://dubium-db.c9yesoygivci.eu-north-1.rds.amazonaws.com:5432/dubium"
    private const val DB_USER = "dubiumuser"  //
    private const val DB_PASSWORD = "Pal1tx23"  //

    // Para AWS RDS, cambiar DB_URL por algo como:
    // private const val DB_URL = "jdbc:postgresql://tu-endpoint.rds.amazonaws.com:5432/dubium"

    /**
     * Crear una nueva conexión a PostgreSQL
     * @return Connection - conexión activa a la base de datos
     * @throws SQLException si no puede conectar
     */
    fun getConnection(): Connection {
        return try {
            DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)
        } catch (e: SQLException) {
            throw SQLException("Error conectando a la base de datos: ${e.message}", e)
        }
    }

    /**
     * Inicializar la base de datos creando las tablas necesarias
     * Se llama al inicio de la aplicación
     */
    fun initDatabase() {
        try {
            println("🔍 Intentando conectar para crear tablas...")
            getConnection().use { conn ->
                println("🔍 Conexión obtenida, creando tabla...")
                createUsersTable(conn)
                println("✅ Base de datos inicializada correctamente")
            }
        } catch (e: SQLException) {
            println("❌ Error SQL inicializando base de datos: ${e.message}")
            e.printStackTrace()
            throw e
        } catch (e: Exception) {
            println("❌ Error general inicializando base de datos: ${e.message}")
            e.printStackTrace()
            throw e
        }
    }

    /**
     * Crear tabla de usuarios si no existe
     */
    private fun createUsersTable(conn: Connection) {
        val createTableSQL = """
            CREATE TABLE IF NOT EXISTS users (
                id SERIAL PRIMARY KEY,
                username VARCHAR(50) UNIQUE NOT NULL,
                password_hash VARCHAR(255) NOT NULL,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            )
        """

        conn.createStatement().use { statement ->
            statement.execute(createTableSQL)
            println("📋 Tabla 'users' verificada/creada")
        }
    }

    // 👤 OPERACIONES DE USUARIOS

    /**
     * Insertar un nuevo usuario en la base de datos
     * @param username - nombre de usuario único
     * @param passwordHash - contraseña hasheada con BCrypt
     * @return UserData - datos del usuario creado
     * @throws SQLException si el usuario ya existe o hay error de BD
     */
    fun insertUser(username: String, passwordHash: String): UserData {
        val insertSQL = """
            INSERT INTO users (username, password_hash) 
            VALUES (?, ?) 
            RETURNING id, username, created_at
        """

        return getConnection().use { conn ->
            conn.prepareStatement(insertSQL).use { stmt ->
                stmt.setString(1, username)
                stmt.setString(2, passwordHash)

                val result = stmt.executeQuery()
                if (result.next()) {
                    UserData(
                        id = result.getInt("id"),
                        username = result.getString("username"),
                        passwordHash = passwordHash, // Lo incluimos para uso interno
                        createdAt = result.getTimestamp("created_at").toLocalDateTime()
                    )
                } else {
                    throw SQLException("Error insertando usuario: no se obtuvo resultado")
                }
            }
        }
    }

    /**
     * Buscar usuario por username
     * @param username - nombre de usuario a buscar
     * @return UserData? - datos del usuario o null si no existe
     */
    fun findUserByUsername(username: String): UserData? {
        val selectSQL = """
            SELECT id, username, password_hash, created_at 
            FROM users 
            WHERE username = ?
        """

        return getConnection().use { conn ->
            conn.prepareStatement(selectSQL).use { stmt ->
                stmt.setString(1, username)

                val result = stmt.executeQuery()
                if (result.next()) {
                    UserData(
                        id = result.getInt("id"),
                        username = result.getString("username"),
                        passwordHash = result.getString("password_hash"),
                        createdAt = result.getTimestamp("created_at").toLocalDateTime()
                    )
                } else {
                    null
                }
            }
        }
    }

    /**
     * Buscar usuario por ID
     * @param userId - ID del usuario a buscar
     * @return UserData? - datos del usuario o null si no existe
     */
    fun findUserById(userId: Int): UserData? {
        val selectSQL = """
            SELECT id, username, password_hash, created_at 
            FROM users 
            WHERE id = ?
        """

        return getConnection().use { conn ->
            conn.prepareStatement(selectSQL).use { stmt ->
                stmt.setInt(1, userId)

                val result = stmt.executeQuery()
                if (result.next()) {
                    UserData(
                        id = result.getInt("id"),
                        username = result.getString("username"),
                        passwordHash = result.getString("password_hash"),
                        createdAt = result.getTimestamp("created_at").toLocalDateTime()
                    )
                } else {
                    null
                }
            }
        }
    }

    /**
     * Obtener todos los usuarios (para debugging)
     * SOLO USAR EN DESARROLLO
     * @return List<UserData> - lista de todos los usuarios
     */
    fun getAllUsers(): List<UserData> {
        val selectSQL = """
            SELECT id, username, password_hash, created_at 
            FROM users 
            ORDER BY created_at DESC
        """

        return getConnection().use { conn ->
            conn.createStatement().use { stmt ->
                val result = stmt.executeQuery(selectSQL)
                val users = mutableListOf<UserData>()

                while (result.next()) {
                    users.add(
                        UserData(
                            id = result.getInt("id"),
                            username = result.getString("username"),
                            passwordHash = result.getString("password_hash"),
                            createdAt = result.getTimestamp("created_at").toLocalDateTime()
                        )
                    )
                }

                users
            }
        }
    }

    /**
     * Verificar si existe un usuario con el username dado
     * @param username - username a verificar
     * @return Boolean - true si existe, false si no
     */
    fun userExists(username: String): Boolean {
        val selectSQL = "SELECT 1 FROM users WHERE username = ? LIMIT 1"

        return getConnection().use { conn ->
            conn.prepareStatement(selectSQL).use { stmt ->
                stmt.setString(1, username)
                val result = stmt.executeQuery()
                result.next()
            }
        }
    }

    /**
     * Obtener conteo total de usuarios
     * @return Int - número total de usuarios registrados
     */
    fun getUserCount(): Int {
        val countSQL = "SELECT COUNT(*) as total FROM users"

        return getConnection().use { conn ->
            conn.createStatement().use { stmt ->
                val result = stmt.executeQuery(countSQL)
                if (result.next()) {
                    result.getInt("total")
                } else {
                    0
                }
            }
        }
    }

    // 🧪 UTILIDADES PARA DESARROLLO/TESTING

    /**
     * Limpiar toda la tabla de usuarios
     * ⚠️ PELIGROSO - Solo usar en desarrollo/testing
     */
    fun clearAllUsers() {
        val deleteSQL = "DELETE FROM users"

        getConnection().use { conn ->
            conn.createStatement().use { stmt ->
                val deletedRows = stmt.executeUpdate(deleteSQL)
                println("🗑️ Eliminados $deletedRows usuarios")
            }
        }
    }

    /**
     * Verificar conectividad con la base de datos
     * @return Boolean - true si puede conectar, false si no
     */
    fun testConnection(): Boolean {
        return try {
            getConnection().use { conn ->
                conn.createStatement().use { stmt ->
                    stmt.executeQuery("SELECT 1").next()
                }
            }
        } catch (e: SQLException) {
            println("❌ Error de conexión: ${e.message}")
            false
        }
    }
}

/**
 * Data class para representar datos de usuario desde la base de datos
 * Incluye la contraseña hasheada para uso interno del servidor
 */
data class UserData(
    val id: Int,
    val username: String,
    val passwordHash: String,        // Contraseña hasheada - NUNCA enviar a cliente
    val createdAt: LocalDateTime
)