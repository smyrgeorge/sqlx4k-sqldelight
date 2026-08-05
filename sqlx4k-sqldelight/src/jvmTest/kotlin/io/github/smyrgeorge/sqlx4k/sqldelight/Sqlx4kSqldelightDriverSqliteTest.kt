package io.github.smyrgeorge.sqlx4k.sqldelight

import app.cash.sqldelight.db.QueryResult
import io.github.smyrgeorge.sqlx4k.ConnectionPool
import io.github.smyrgeorge.sqlx4k.sqlite.ISQLite
import io.github.smyrgeorge.sqlx4k.sqlite.sqlite
import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/**
 * Integration tests for Sqlx4kSqldelightDriver with SQLite.
 *
 * Unlike the PostgreSQL and MySQL tests no container is needed: each test runs against a
 * fresh in-memory database. In-memory SQLite databases are isolated per connection, so the
 * connection pool is limited to a single connection.
 *
 * Tests the SQLDelight driver wrapper functionality with SQLite:
 * - Basic query execution
 * - Parameter binding
 * - Result set iteration
 * - Transaction support
 */
class Sqlx4kSqldelightDriverSqliteTest {

    private lateinit var sqlx4kDriver: ISQLite
    private lateinit var sqldelightDriver: Sqlx4kSqldelightDriver<ISQLite>

    @BeforeTest
    fun setup() {
        runBlocking {
            val options = ConnectionPool.Options.builder()
                .minConnections(1)
                .maxConnections(1)
                .build()

            sqlx4kDriver = sqlite(
                url = "sqlite::memory:",
                options = options
            )

            sqldelightDriver = Sqlx4kSqldelightDriver(sqlx4kDriver)

            // Create test table
            sqlx4kDriver.execute(
                """
                CREATE TABLE IF NOT EXISTS test_users (
                    id TEXT PRIMARY KEY,
                    name TEXT NOT NULL,
                    email TEXT,
                    age INTEGER,
                    active INTEGER DEFAULT 1,
                    data BLOB
                )
                """.trimIndent()
            ).getOrThrow()
        }
    }

    @AfterTest
    fun teardown() {
        sqldelightDriver.close()
    }

    @Test
    fun shouldExecuteInsertStatement() = runBlocking {
        val driver = sqldelightDriver
        val id = Uuid.random()

        // SQLite uses ? for positional parameters
        val result = driver.execute(
            identifier = null,
            sql = "INSERT INTO test_users (id, name, email, age) VALUES (?, ?, ?, ?)",
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
        val driver = sqldelightDriver
        val id = Uuid.random()
        val name = "Jane Smith"

        // Insert test data
        driver.execute(
            identifier = null,
            sql = "INSERT INTO test_users (id, name, email) VALUES (?, ?, ?)",
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
            sql = "SELECT name FROM test_users WHERE id = ?",
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
        val driver = sqldelightDriver
        val id = Uuid.random()

        // Insert with null email
        driver.execute(
            identifier = null,
            sql = "INSERT INTO test_users (id, name, email) VALUES (?, ?, ?)",
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
            sql = "SELECT email FROM test_users WHERE id = ?",
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
        val driver = sqldelightDriver

        // Start transaction - verify it can be created
        val transaction = driver.newTransaction().await()
        assertNotNull(transaction)
        assertEquals(transaction, driver.currentTransaction())
    }

    @Test
    fun shouldIterateMultipleRows() = runBlocking {
        val driver = sqldelightDriver

        // Insert multiple records
        val names = listOf("Alice", "Bob", "Charlie")
        names.forEach { name ->
            driver.execute(
                identifier = null,
                sql = "INSERT INTO test_users (id, name) VALUES (?, ?)",
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
            sql = "SELECT name FROM test_users WHERE name IN (?, ?, ?) ORDER BY name",
            mapper = { cursor ->
                QueryResult.AsyncValue {
                    while (cursor.next().await()) {
                        cursor.getString(0)?.let { foundNames.add(it) }
                    }
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
        val driver = sqldelightDriver
        val id = Uuid.random()

        // Insert with boolean
        driver.execute(
            identifier = null,
            sql = "INSERT INTO test_users (id, name, active) VALUES (?, ?, ?)",
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
            sql = "SELECT active FROM test_users WHERE id = ?",
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
        val driver = sqldelightDriver
        val id = Uuid.random()
        val age = 42L

        // Insert with numeric value
        driver.execute(
            identifier = null,
            sql = "INSERT INTO test_users (id, name, age) VALUES (?, ?, ?)",
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
            sql = "SELECT age FROM test_users WHERE id = ?",
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

    @Test
    fun shouldHandleByteArrayValues() = runBlocking {
        val driver = sqldelightDriver
        val id = Uuid.random()
        val data = byteArrayOf(1, 2, 3, -1, 0, 127, -128)

        // Insert with a byte array
        driver.execute(
            identifier = null,
            sql = "INSERT INTO test_users (id, name, data) VALUES (?, ?, ?)",
            parameters = 3
        ) {
            bindString(0, id.toString())
            bindString(1, "Binary User")
            bindBytes(2, data)
        }.await()

        // Query and verify the byte array round-trips
        var found: ByteArray? = null
        driver.executeQuery(
            identifier = null,
            sql = "SELECT data FROM test_users WHERE id = ?",
            mapper = { cursor ->
                QueryResult.AsyncValue {
                    if (cursor.next().await()) {
                        found = cursor.getBytes(0)
                    }
                    Unit
                }
            },
            parameters = 1
        ) {
            bindString(0, id.toString())
        }.await()

        assertContentEquals(data, found)
    }
}
