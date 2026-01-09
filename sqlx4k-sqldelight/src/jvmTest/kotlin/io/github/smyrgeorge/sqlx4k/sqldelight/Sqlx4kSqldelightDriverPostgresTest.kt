package io.github.smyrgeorge.sqlx4k.sqldelight

import app.cash.sqldelight.db.QueryResult
import io.github.smyrgeorge.sqlx4k.ConnectionPool
import io.github.smyrgeorge.sqlx4k.postgres.IPostgresSQL
import io.github.smyrgeorge.sqlx4k.postgres.postgreSQL
import kotlinx.coroutines.runBlocking
import org.testcontainers.containers.PostgreSQLContainer
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Integration tests for Sqlx4kSqldelightDriver with PostgreSQL using TestContainers.
 *
 * Tests the SQLDelight driver wrapper functionality:
 * - Basic query execution
 * - Parameter binding
 * - Result set iteration
 * - Transaction support
 */
@OptIn(ExperimentalUuidApi::class)
class Sqlx4kSqldelightDriverPostgresTest {

    private val postgres = PostgreSQLContainer<Nothing>("postgres:16-alpine").apply {
        withDatabaseName("test_db")
        withUsername("test")
        withPassword("test")
    }

    private var dockerAvailable: Boolean = false
    private var sqlx4kDriver: IPostgresSQL? = null
    private var sqldelightDriver: Sqlx4kSqldelightDriver<IPostgresSQL>? = null

    @BeforeTest
    fun setup() {
        println("=== Starting Docker detection ===")
        // Try to start the container directly instead of using TestContainers' isDockerAvailable
        // which has issues in WSL2 environments
        dockerAvailable = runCatching {
            println("Attempting to start PostgreSQL container...")
            postgres.start()
            println("PostgreSQL container started successfully")
            true
        }.getOrElse { e ->
            println("Docker/container start failed with exception: ${e.javaClass.name}")
            println("Error: ${e.message}")
            false
        }
        if (!dockerAvailable) {
            println("Docker not available, skipping Sqlx4kSqldelightDriverPostgresTest...")
            return
        }
        println("Docker available, running Sqlx4kSqldelightDriverPostgresTest...")

        runBlocking {
            val options = ConnectionPool.Options.builder()
                .maxConnections(10)
                .build()

            sqlx4kDriver = postgreSQL(
                url = "postgresql://${postgres.host}:${postgres.firstMappedPort}/${postgres.databaseName}",
                username = postgres.username,
                password = postgres.password,
                options = options
            )

            sqldelightDriver = Sqlx4kSqldelightDriver(sqlx4kDriver!!)

            // Create test table
            sqlx4kDriver!!.execute(
                """
                CREATE TABLE IF NOT EXISTS test_users (
                    id UUID PRIMARY KEY,
                    name VARCHAR(255) NOT NULL,
                    email VARCHAR(255),
                    age INTEGER,
                    active BOOLEAN DEFAULT true
                )
                """.trimIndent()
            ).getOrThrow()
        }
    }

    @AfterTest
    fun teardown() {
        if (!dockerAvailable) {
            return
        }
        sqldelightDriver?.close()
        postgres.stop()
    }

    @Test
    fun shouldExecuteInsertStatement() = runBlocking {
        if (!dockerAvailable) return@runBlocking

        val driver = sqldelightDriver ?: return@runBlocking
        val id = Uuid.random()

        val result = driver.execute(
            identifier = null,
            sql = "INSERT INTO test_users (id, name, email, age) VALUES ($1, $2, $3, $4)",
            parameters = 4
        ) {
            bindString(0, id.toString())
            bindString(1, "John Doe")
            bindString(2, "john@example.com")
            bindLong(3, 30)
        }

        assertTrue(result is QueryResult.AsyncValue)
        val affected = result.await()
        assertEquals(1L, affected)
    }

    @Test
    fun shouldExecuteSelectQuery() = runBlocking {
        if (!dockerAvailable) return@runBlocking

        val driver = sqldelightDriver ?: return@runBlocking
        val id = Uuid.random()
        val name = "Jane Smith"

        // Insert test data
        driver.execute(
            identifier = null,
            sql = "INSERT INTO test_users (id, name, email) VALUES ($1, $2, $3)",
            parameters = 3
        ) {
            bindString(0, id.toString())
            bindString(1, name)
            bindString(2, "jane@example.com")
        }.await()

        // Query the data
        var foundName: String? = null
        driver.executeQuery(
            identifier = null,
            sql = "SELECT name FROM test_users WHERE id = $1",
            mapper = { cursor ->
                QueryResult.AsyncValue {
                    if (cursor.next().await()) {
                        foundName = cursor.getString(0)
                    }
                    Unit
                }
            },
            parameters = 1
        ) {
            bindString(0, id.toString())
        }.await()

        assertEquals(name, foundName)
    }

    @Test
    fun shouldHandleNullValues() = runBlocking {
        if (!dockerAvailable) return@runBlocking

        val driver = sqldelightDriver ?: return@runBlocking
        val id = Uuid.random()

        // Insert with null email
        driver.execute(
            identifier = null,
            sql = "INSERT INTO test_users (id, name, email) VALUES ($1, $2, $3)",
            parameters = 3
        ) {
            bindString(0, id.toString())
            bindString(1, "No Email User")
            bindString(2, null)
        }.await()

        // Query and verify null
        var email: String? = "not_null"
        driver.executeQuery(
            identifier = null,
            sql = "SELECT email FROM test_users WHERE id = $1",
            mapper = { cursor ->
                QueryResult.AsyncValue {
                    if (cursor.next().await()) {
                        email = cursor.getString(0)
                    }
                    Unit
                }
            },
            parameters = 1
        ) {
            bindString(0, id.toString())
        }.await()

        assertEquals(null, email)
    }

    @Test
    fun shouldSupportNewTransaction() = runBlocking {
        if (!dockerAvailable) return@runBlocking

        val driver = sqldelightDriver ?: return@runBlocking

        // Start transaction - verify it can be created
        val transaction = driver.newTransaction().await()
        assertNotNull(transaction)
        assertEquals(transaction, driver.currentTransaction())
    }

    @Test
    fun shouldIterateMultipleRows() = runBlocking {
        if (!dockerAvailable) return@runBlocking

        val driver = sqldelightDriver ?: return@runBlocking

        // Insert multiple records
        val names = listOf("Alice", "Bob", "Charlie")
        names.forEach { name ->
            driver.execute(
                identifier = null,
                sql = "INSERT INTO test_users (id, name) VALUES ($1, $2)",
                parameters = 2
            ) {
                bindString(0, Uuid.random().toString())
                bindString(1, name)
            }.await()
        }

        // Query all and collect names
        val foundNames = mutableListOf<String>()
        driver.executeQuery(
            identifier = null,
            sql = "SELECT name FROM test_users WHERE name IN ($1, $2, $3) ORDER BY name",
            mapper = { cursor ->
                QueryResult.AsyncValue {
                    while (cursor.next().await()) {
                        cursor.getString(0)?.let { foundNames.add(it) }
                    }
                    Unit
                }
            },
            parameters = 3
        ) {
            bindString(0, "Alice")
            bindString(1, "Bob")
            bindString(2, "Charlie")
        }.await()

        assertEquals(names.sorted(), foundNames)
    }

    @Test
    fun shouldHandleBooleanValues() = runBlocking {
        if (!dockerAvailable) return@runBlocking

        val driver = sqldelightDriver ?: return@runBlocking
        val id = Uuid.random()

        // Insert with boolean
        driver.execute(
            identifier = null,
            sql = "INSERT INTO test_users (id, name, active) VALUES ($1, $2, $3)",
            parameters = 3
        ) {
            bindString(0, id.toString())
            bindString(1, "Active User")
            bindBoolean(2, true)
        }.await()

        // Query and verify boolean
        var isActive: Boolean? = null
        driver.executeQuery(
            identifier = null,
            sql = "SELECT active FROM test_users WHERE id = $1",
            mapper = { cursor ->
                QueryResult.AsyncValue {
                    if (cursor.next().await()) {
                        isActive = cursor.getBoolean(0)
                    }
                    Unit
                }
            },
            parameters = 1
        ) {
            bindString(0, id.toString())
        }.await()

        assertEquals(true, isActive)
    }

    @Test
    fun shouldHandleNumericValues() = runBlocking {
        if (!dockerAvailable) return@runBlocking

        val driver = sqldelightDriver ?: return@runBlocking
        val id = Uuid.random()
        val age = 42L

        // Insert with numeric value
        driver.execute(
            identifier = null,
            sql = "INSERT INTO test_users (id, name, age) VALUES ($1, $2, $3)",
            parameters = 3
        ) {
            bindString(0, id.toString())
            bindString(1, "Aged User")
            bindLong(2, age)
        }.await()

        // Query and verify numeric
        var foundAge: Long? = null
        driver.executeQuery(
            identifier = null,
            sql = "SELECT age FROM test_users WHERE id = $1",
            mapper = { cursor ->
                QueryResult.AsyncValue {
                    if (cursor.next().await()) {
                        foundAge = cursor.getLong(0)
                    }
                    Unit
                }
            },
            parameters = 1
        ) {
            bindString(0, id.toString())
        }.await()

        assertEquals(age, foundAge)
    }
}
