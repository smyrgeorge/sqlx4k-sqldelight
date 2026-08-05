package io.github.smyrgeorge.sqlx4k.sqldelight.dialect.postgres

import app.cash.sqldelight.dialect.api.DialectType
import app.cash.sqldelight.dialect.api.IntermediateType
import app.cash.sqldelight.dialect.api.PrimitiveType
import app.cash.sqldelight.dialect.api.QueryWithResults
import app.cash.sqldelight.dialect.api.TypeResolver
import com.alecstrong.sql.psi.core.psi.SqlExpr
import com.alecstrong.sql.psi.core.psi.SqlFunctionExpr
import com.alecstrong.sql.psi.core.psi.SqlStmt
import com.alecstrong.sql.psi.core.psi.SqlTypeName
import com.intellij.psi.PsiElement
import com.squareup.kotlinpoet.CodeBlock
import io.github.smyrgeorge.sqlx4k.sqldelight.dialect.postgres.PostgresSqlSqlx4kDialect.PostgreSqlSqlx4kTypeResolver
import io.github.smyrgeorge.sqlx4k.sqldelight.dialect.postgres.PostgresSqlSqlx4kDialect.PostgreSqlType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue
import app.cash.sqldelight.dialects.postgresql.PostgreSqlType as UpstreamPostgreSqlType

/**
 * Tests for the parts of the dialect that this module owns: the runtime type configuration,
 * the multiplatform dialect types (Kotlin types, binder and cursor code generation), and the
 * remapping of the upstream JVM-only dialect types.
 *
 * The full `.sq`-to-Kotlin code generation (grammar to [PostgreSqlType] mapping) runs through
 * the SqlDelight compiler and is exercised by the `examples:postgres-sqldelight` module, which
 * generates and compiles code with this dialect on every build.
 */
class PostgresSqlSqlx4kDialectTests {

    private val dialect = PostgresSqlSqlx4kDialect()

    private val parentResolver = object : TypeResolver {
        override fun resolvedType(expr: SqlExpr): IntermediateType = error("Not used")
        override fun argumentType(parent: PsiElement, argument: SqlExpr): IntermediateType = error("Not used")
        override fun functionType(functionExpr: SqlFunctionExpr): IntermediateType? = error("Not used")
        override fun definitionType(typeName: SqlTypeName): IntermediateType = error("Not used")
        override fun queryWithResults(sqlStmt: SqlStmt): QueryWithResults? = error("Not used")
    }

    private val resolver = PostgreSqlSqlx4kTypeResolver(parentResolver)

    private fun DialectType.binder(): String =
        prepareStatementBinder(CodeBlock.of("0"), CodeBlock.of("value")).toString().trim()

    private fun DialectType.getter(): String =
        cursorGetter(0, "cursor").toString()

    // ---- Dialect configuration ----

    @Test
    fun `async runtime types point to the sqlx4k runtime`() {
        assertEquals(
            "io.github.smyrgeorge.sqlx4k.sqldelight.SqlDelightCursor",
            dialect.asyncRuntimeTypes.cursorType.toString()
        )
        assertEquals(
            "io.github.smyrgeorge.sqlx4k.sqldelight.SqlDelightPreparedStatement",
            dialect.asyncRuntimeTypes.preparedStatementType.toString()
        )
    }

    @Test
    fun `sync runtime types are not supported`() {
        assertFailsWith<IllegalStateException> { dialect.runtimeTypes }
    }

    @Test
    fun `type resolver is the sqlx4k resolver`() {
        assertIs<PostgreSqlSqlx4kTypeResolver>(dialect.typeResolver(parentResolver))
    }

    // ---- Dialect types ----

    @Test
    fun `dialect types map to multiplatform kotlin types`() {
        assertEquals("kotlin.Short", PostgreSqlType.SMALL_INT.javaType.toString())
        assertEquals("kotlin.Int", PostgreSqlType.INTEGER.javaType.toString())
        assertEquals("kotlin.Long", PostgreSqlType.BIG_INT.javaType.toString())
        assertEquals("kotlin.Double", PostgreSqlType.NUMERIC.javaType.toString())
        assertEquals("kotlinx.datetime.LocalDate", PostgreSqlType.DATE.javaType.toString())
        assertEquals("kotlinx.datetime.LocalTime", PostgreSqlType.TIME.javaType.toString())
        assertEquals("kotlinx.datetime.LocalDateTime", PostgreSqlType.TIMESTAMP.javaType.toString())
        assertEquals("kotlinx.datetime.DateTimePeriod", PostgreSqlType.INTERVAL.javaType.toString())
        assertEquals("kotlin.time.Instant", PostgreSqlType.TIMESTAMP_TIMEZONE.javaType.toString())
        assertEquals("kotlin.uuid.Uuid", PostgreSqlType.UUID.javaType.toString())
    }

    @Test
    fun `dialect types bind through the sqlx4k prepared statement`() {
        assertEquals("bindShort(0, value)", PostgreSqlType.SMALL_INT.binder())
        assertEquals("bindInt(0, value)", PostgreSqlType.INTEGER.binder())
        assertEquals("bindLong(0, value)", PostgreSqlType.BIG_INT.binder())
        assertEquals("bindDouble(0, value)", PostgreSqlType.NUMERIC.binder())
        assertEquals("bindDate(0, value)", PostgreSqlType.DATE.binder())
        assertEquals("bindTime(0, value)", PostgreSqlType.TIME.binder())
        assertEquals("bindLocalTimestamp(0, value)", PostgreSqlType.TIMESTAMP.binder())
        assertEquals("bindInterval(0, value)", PostgreSqlType.INTERVAL.binder())
        assertEquals("bindTimestamp(0, value)", PostgreSqlType.TIMESTAMP_TIMEZONE.binder())
        assertEquals("bindUuid(0, value)", PostgreSqlType.UUID.binder())
    }

    @Test
    fun `dialect types read through the sqlx4k cursor`() {
        assertEquals("cursor.getShort(0)", PostgreSqlType.SMALL_INT.getter())
        assertEquals("cursor.getInt(0)", PostgreSqlType.INTEGER.getter())
        assertEquals("cursor.getLong(0)", PostgreSqlType.BIG_INT.getter())
        assertEquals("cursor.getDouble(0)", PostgreSqlType.NUMERIC.getter())
        assertEquals("cursor.getDate(0)", PostgreSqlType.DATE.getter())
        assertEquals("cursor.getTime(0)", PostgreSqlType.TIME.getter())
        assertEquals("cursor.getLocalTimestamp(0)", PostgreSqlType.TIMESTAMP.getter())
        assertEquals("cursor.getInterval(0)", PostgreSqlType.INTERVAL.getter())
        assertEquals("cursor.getTimestamp(0)", PostgreSqlType.TIMESTAMP_TIMEZONE.getter())
        assertEquals("cursor.getUuid(0)", PostgreSqlType.UUID.getter())
    }

    // ---- Remapping of upstream (JVM-only) types ----

    @Test
    fun `upstream dialect types are remapped to multiplatform types`() {
        with(resolver) {
            assertSame(PostgreSqlType.SMALL_INT, IntermediateType(UpstreamPostgreSqlType.SMALL_INT).remapped().dialectType)
            assertSame(PostgreSqlType.INTEGER, IntermediateType(UpstreamPostgreSqlType.INTEGER).remapped().dialectType)
            assertSame(PostgreSqlType.BIG_INT, IntermediateType(UpstreamPostgreSqlType.BIG_INT).remapped().dialectType)
            assertSame(PostgreSqlType.NUMERIC, IntermediateType(UpstreamPostgreSqlType.NUMERIC).remapped().dialectType)
            assertSame(PostgreSqlType.DATE, IntermediateType(UpstreamPostgreSqlType.DATE).remapped().dialectType)
            assertSame(PostgreSqlType.TIME, IntermediateType(UpstreamPostgreSqlType.TIME).remapped().dialectType)
            assertSame(PostgreSqlType.TIMESTAMP, IntermediateType(UpstreamPostgreSqlType.TIMESTAMP).remapped().dialectType)
            assertSame(
                PostgreSqlType.TIMESTAMP_TIMEZONE,
                IntermediateType(UpstreamPostgreSqlType.TIMESTAMP_TIMEZONE).remapped().dialectType
            )
            assertSame(PostgreSqlType.INTERVAL, IntermediateType(UpstreamPostgreSqlType.INTERVAL).remapped().dialectType)
            assertSame(PostgreSqlType.UUID, IntermediateType(UpstreamPostgreSqlType.UUID).remapped().dialectType)
        }
    }

    @Test
    fun `upstream string-like dialect types are remapped to TEXT`() {
        val stringLike = listOf(
            UpstreamPostgreSqlType.JSON,
            UpstreamPostgreSqlType.TSVECTOR,
            UpstreamPostgreSqlType.TSQUERY,
            UpstreamPostgreSqlType.TSRANGE,
            UpstreamPostgreSqlType.TSTZRANGE,
            UpstreamPostgreSqlType.TSMULTIRANGE,
            UpstreamPostgreSqlType.TSTZMULTIRANGE,
            UpstreamPostgreSqlType.XML,
            UpstreamPostgreSqlType.ENUM,
            UpstreamPostgreSqlType.GEOMETRY,
            UpstreamPostgreSqlType.GEOGRAPHY,
        )
        with(resolver) {
            stringLike.forEach { upstream ->
                assertSame(PrimitiveType.TEXT, IntermediateType(upstream).remapped().dialectType, "for $upstream")
            }
        }
    }

    @Test
    fun `remapping preserves nullability`() {
        with(resolver) {
            val nullable = IntermediateType(UpstreamPostgreSqlType.TIMESTAMP_TIMEZONE).asNullable().remapped()
            assertTrue(nullable.javaType.isNullable)
            assertEquals("kotlin.time.Instant", nullable.javaType.copy(nullable = false).toString())

            val nonNullable = IntermediateType(UpstreamPostgreSqlType.TIMESTAMP_TIMEZONE).remapped()
            assertFalse(nonNullable.javaType.isNullable)
        }
    }

    @Test
    fun `multiplatform types pass through the remapping unchanged`() {
        with(resolver) {
            val primitive = IntermediateType(PrimitiveType.TEXT)
            assertSame(primitive, primitive.remapped())

            val own = IntermediateType(PostgreSqlType.UUID)
            assertSame(own, own.remapped())
        }
    }
}
