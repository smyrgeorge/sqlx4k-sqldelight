package io.github.smyrgeorge.sqlx4k.sqldelight

import app.cash.sqldelight.Query
import app.cash.sqldelight.SuspendingTransacterImpl
import app.cash.sqldelight.db.QueryResult
import io.github.smyrgeorge.sqlx4k.Connection
import io.github.smyrgeorge.sqlx4k.Dialect
import io.github.smyrgeorge.sqlx4k.Driver
import io.github.smyrgeorge.sqlx4k.ResultSet
import io.github.smyrgeorge.sqlx4k.SQLError
import io.github.smyrgeorge.sqlx4k.Statement
import io.github.smyrgeorge.sqlx4k.Transaction
import io.github.smyrgeorge.sqlx4k.ValueEncoderRegistry
import io.github.smyrgeorge.sqlx4k.impl.migrate.Migration
import io.github.smyrgeorge.sqlx4k.impl.migrate.MigrationFile
import io.github.smyrgeorge.sqlx4k.impl.migrate.Migrator
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Duration

class Sqlx4kSqldelightDriverTests {

    // ---- execute ----

    @Test
    fun `execute passes the bound statement to the underlying driver`() = runTest {
        val fake = FakeDriver()
        val driver = Sqlx4kSqldelightDriver(fake)

        val affected = driver.execute(
            identifier = null,
            sql = "INSERT INTO users (id, name) VALUES ($1, $2)",
            parameters = 2
        ) {
            bindLong(0, 1)
            bindString(1, "Alice")
        }.await()

        assertEquals(1L, affected)
        val statement = assertIs<ExtendedStatement>(fake.executedStatements.single())
        val query = statement.renderNativeQuery(Dialect.PostgreSQL, ValueEncoderRegistry.EMPTY)
        assertEquals("INSERT INTO users (id, name) VALUES ($1, $2)", query.sql)
        assertEquals(listOf<Any?>(1L, "Alice"), query.values)
    }

    @Test
    fun `execute returns the affected rows reported by the underlying driver`() = runTest {
        val fake = FakeDriver(executeResult = Result.success(42L))
        val driver = Sqlx4kSqldelightDriver(fake)

        val affected = driver.execute(null, "DELETE FROM users", 0, null).await()

        assertEquals(42L, affected)
    }

    @Test
    fun `execute without binders executes a statement with no values`() = runTest {
        val fake = FakeDriver()
        val driver = Sqlx4kSqldelightDriver(fake)

        driver.execute(null, "DELETE FROM users", 0, null).await()

        val statement = fake.executedStatements.single()
        val query = statement.renderNativeQuery(Dialect.PostgreSQL, ValueEncoderRegistry.EMPTY)
        assertEquals("DELETE FROM users", query.sql)
        assertEquals(emptyList(), query.values)
    }

    @Test
    fun `execute propagates errors from the underlying driver`() = runTest {
        val error = SQLError(SQLError.Code.Database, "boom")
        val fake = FakeDriver(executeResult = Result.failure(error))
        val driver = Sqlx4kSqldelightDriver(fake)

        val exception = assertFailsWith<SQLError> {
            driver.execute(null, "DELETE FROM users", 0, null).await()
        }
        assertEquals(SQLError.Code.Database, exception.code)
    }

    // ---- executeQuery ----

    @Test
    fun `executeQuery passes the bound statement to the underlying driver`() = runTest {
        val fake = FakeDriver()
        val driver = Sqlx4kSqldelightDriver(fake)

        driver.executeQuery(
            identifier = null,
            sql = "SELECT name FROM users WHERE id = $1",
            mapper = { QueryResult.AsyncValue { } },
            parameters = 1
        ) {
            bindLong(0, 7)
        }.await()

        val statement = assertIs<ExtendedStatement>(fake.fetchedStatements.single())
        val query = statement.renderNativeQuery(Dialect.PostgreSQL, ValueEncoderRegistry.EMPTY)
        assertEquals(listOf<Any?>(7L), query.values)
    }

    @Test
    fun `executeQuery maps the result set rows through the cursor`() = runTest {
        val fake = FakeDriver(
            fetchAllResult = Result.success(
                resultSetOf(
                    row("1", "Alice"),
                    row("2", "Bob"),
                )
            )
        )
        val driver = Sqlx4kSqldelightDriver(fake)

        val users = driver.executeQuery(
            identifier = null,
            sql = "SELECT id, name FROM users",
            mapper = { cursor ->
                QueryResult.AsyncValue {
                    buildList {
                        while (cursor.next().await()) {
                            add(cursor.getLong(0)!! to cursor.getString(1)!!)
                        }
                    }
                }
            },
            parameters = 0,
            binders = null
        ).await()

        assertEquals(listOf(1L to "Alice", 2L to "Bob"), users)
    }

    @Test
    fun `executeQuery propagates errors from the underlying driver`() = runTest {
        val error = SQLError(SQLError.Code.Database, "boom")
        val fake = FakeDriver(fetchAllResult = Result.failure(error))
        val driver = Sqlx4kSqldelightDriver(fake)

        val exception = assertFailsWith<SQLError> {
            driver.executeQuery(
                identifier = null,
                sql = "SELECT 1",
                mapper = { QueryResult.AsyncValue { } },
                parameters = 0,
                binders = null
            ).await()
        }
        assertEquals(SQLError.Code.Database, exception.code)
    }

    // ---- transactions ----

    @Test
    fun `a successful transaction begins and commits`() = runTest {
        val fake = FakeDriver()
        val driver = Sqlx4kSqldelightDriver(fake)
        val transacter = object : SuspendingTransacterImpl(driver) {}

        transacter.transaction {
            assertNotNull(driver.currentTransaction())
        }

        val transaction = fake.transactions.single()
        assertTrue(transaction.commited)
        assertFalse(transaction.rollbacked)
        assertNull(driver.currentTransaction())
    }

    @Test
    fun `a failed transaction rolls back`() = runTest {
        val fake = FakeDriver()
        val driver = Sqlx4kSqldelightDriver(fake)
        val transacter = object : SuspendingTransacterImpl(driver) {}

        class TestException : RuntimeException("rollback, please")

        assertFailsWith<TestException> {
            transacter.transaction {
                throw TestException()
            }
        }

        val transaction = fake.transactions.single()
        assertFalse(transaction.commited)
        assertTrue(transaction.rollbacked)
        assertNull(driver.currentTransaction())
    }

    @Test
    fun `statements inside a transaction run while the transaction is active`() = runTest {
        val fake = FakeDriver()
        val driver = Sqlx4kSqldelightDriver(fake)
        val transacter = object : SuspendingTransacterImpl(driver) {}

        transacter.transaction {
            driver.execute(null, "INSERT INTO users (id) VALUES ($1)", 1) {
                bindLong(0, 1)
            }.await()
        }

        assertEquals(1, fake.executedStatements.size)
        assertTrue(fake.transactions.single().commited)
    }

    @Test
    fun `currentTransaction is null outside a transaction`() {
        val driver = Sqlx4kSqldelightDriver(FakeDriver())
        assertNull(driver.currentTransaction())
    }

    @Test
    fun `newTransaction returns the existing transaction while one is active`() = runTest {
        val fake = FakeDriver()
        val driver = Sqlx4kSqldelightDriver(fake)
        val transacter = object : SuspendingTransacterImpl(driver) {}

        transacter.transaction {
            val current = driver.currentTransaction()
            val again = driver.newTransaction().await()
            assertSame(current, again)
        }

        // Only one transaction was ever started on the underlying driver.
        assertEquals(1, fake.transactions.size)
    }

    // ---- close and listeners ----

    @Test
    fun `close closes the underlying driver`() {
        val fake = FakeDriver()
        val driver = Sqlx4kSqldelightDriver(fake)

        driver.close()

        assertTrue(fake.closed)
    }

    @Test
    fun `listener registration is a no-op`() {
        val driver = Sqlx4kSqldelightDriver(FakeDriver())
        val listener = Query.Listener { }

        driver.addListener("users", listener = listener)
        driver.notifyListeners("users")
        driver.removeListener("users", listener = listener)
    }

    // ---- fixtures ----

    private fun row(vararg values: String?): ResultSet.Row =
        ResultSet.Row(values.mapIndexed { index, value ->
            ResultSet.Row.Column(ordinal = index, name = "c$index", type = "text", value = value)
        })

    private fun resultSetOf(vararg rows: ResultSet.Row): ResultSet {
        val metadata =
            if (rows.isEmpty()) ResultSet.Metadata(emptyList())
            else rows.first().toMetadata()
        return ResultSet(rows.toList(), null, metadata)
    }

    /**
     * A [Driver] fake that records the executed statements and returns canned results,
     * so the SQLDelight driver can be tested without a database.
     */
    private class FakeDriver(
        var executeResult: Result<Long> = Result.success(1L),
        var fetchAllResult: Result<ResultSet> = Result.success(
            ResultSet(emptyList(), null, ResultSet.Metadata(emptyList()))
        ),
    ) : Driver {
        override val encoders: ValueEncoderRegistry = ValueEncoderRegistry()

        val executedStatements = mutableListOf<Statement>()
        val fetchedStatements = mutableListOf<Statement>()
        val transactions = mutableListOf<FakeTransaction>()
        var closed = false

        override suspend fun execute(sql: String): Result<Long> = executeResult

        override suspend fun execute(statement: Statement): Result<Long> {
            executedStatements += statement
            return executeResult
        }

        override suspend fun fetchAll(sql: String): Result<ResultSet> = fetchAllResult

        override suspend fun fetchAll(statement: Statement): Result<ResultSet> {
            fetchedStatements += statement
            return fetchAllResult
        }

        override suspend fun begin(): Result<Transaction> =
            Result.success(FakeTransaction(this).also { transactions += it })

        override fun poolSize(): Int = 0
        override fun poolIdleSize(): Int = 0

        override suspend fun acquire(): Result<Connection> =
            error("Not supported by FakeDriver.")

        override suspend fun close(): Result<Unit> {
            closed = true
            return Result.success(Unit)
        }

        override suspend fun migrate(
            path: String,
            table: String,
            schema: String?,
            createSchema: Boolean,
            afterStatementExecution: suspend (Statement, Duration) -> Unit,
            afterFileMigration: suspend (Migration, Duration) -> Unit,
        ): Result<Migrator.Results> = error("Not supported by FakeDriver.")

        override suspend fun migrate(
            supplier: () -> List<MigrationFile>,
            table: String,
            schema: String?,
            createSchema: Boolean,
            afterStatementExecution: suspend (Statement, Duration) -> Unit,
            afterFileMigration: suspend (Migration, Duration) -> Unit,
        ): Result<Migrator.Results> = error("Not supported by FakeDriver.")

        override suspend fun migrate(
            files: List<MigrationFile>,
            table: String,
            schema: String?,
            createSchema: Boolean,
            afterStatementExecution: suspend (Statement, Duration) -> Unit,
            afterFileMigration: suspend (Migration, Duration) -> Unit,
        ): Result<Migrator.Results> = error("Not supported by FakeDriver.")
    }

    /** A [Transaction] fake that records commit/rollback calls. */
    private class FakeTransaction(private val driver: FakeDriver) : Transaction {
        override var status: Transaction.Status = Transaction.Status.Open
        override var commited: Boolean = false
        override var rollbacked: Boolean = false

        override val encoders: ValueEncoderRegistry = driver.encoders

        override suspend fun commit(): Result<Unit> {
            assertIsOpen()
            commited = true
            status = Transaction.Status.Closed
            return Result.success(Unit)
        }

        override suspend fun rollback(): Result<Unit> {
            assertIsOpen()
            rollbacked = true
            status = Transaction.Status.Closed
            return Result.success(Unit)
        }

        override suspend fun execute(sql: String): Result<Long> = driver.execute(sql)
        override suspend fun execute(statement: Statement): Result<Long> = driver.execute(statement)
        override suspend fun fetchAll(sql: String): Result<ResultSet> = driver.fetchAll(sql)
        override suspend fun fetchAll(statement: Statement): Result<ResultSet> = driver.fetchAll(statement)
    }
}
