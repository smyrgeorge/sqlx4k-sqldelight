package io.github.smyrgeorge.sqlx4k.sqldelight.dialect.mysql

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
import io.github.smyrgeorge.sqlx4k.sqldelight.dialect.mysql.MySqlSqlx4kDialect.MySqlSqlx4kTypeResolver
import io.github.smyrgeorge.sqlx4k.sqldelight.dialect.mysql.MySqlSqlx4kDialect.MySqlType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Tests for the parts of the dialect that this module owns: the runtime type configuration,
 * the multiplatform dialect types (Kotlin types, binder and cursor code generation), and the
 * remapping of the upstream JVM-only dialect types.
 *
 * The full `.sq`-to-Kotlin code generation (grammar to [MySqlType] mapping) runs through the
 * SqlDelight compiler and is exercised by the `examples:mysql-sqldelight` module, which
 * generates and compiles code with this dialect on every build.
 */
class MySqlSqlx4kDialectTests {

    private val dialect = MySqlSqlx4kDialect()

    private val parentResolver = object : TypeResolver {
        override fun resolvedType(expr: SqlExpr): IntermediateType = error("Not used")
        override fun argumentType(parent: PsiElement, argument: SqlExpr): IntermediateType = error("Not used")
        override fun functionType(functionExpr: SqlFunctionExpr): IntermediateType? = error("Not used")
        override fun definitionType(typeName: SqlTypeName): IntermediateType = error("Not used")
        override fun queryWithResults(sqlStmt: SqlStmt): QueryWithResults? = error("Not used")
    }

    private val resolver = MySqlSqlx4kTypeResolver(parentResolver)

    /**
     * The upstream enum (`app.cash.sqldelight.dialects.mysql.MySqlType`) is internal, so its
     * entries are looked up reflectively (Kotlin internal classes are public in the bytecode).
     */
    private fun upstream(name: String): DialectType {
        val entries = Class.forName("app.cash.sqldelight.dialects.mysql.MySqlType").enumConstants
        return entries.first { (it as Enum<*>).name == name } as DialectType
    }

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
        assertIs<MySqlSqlx4kTypeResolver>(dialect.typeResolver(parentResolver))
    }

    // ---- Dialect types ----

    @Test
    fun `dialect types map to multiplatform kotlin types`() {
        assertEquals("kotlin.Boolean", MySqlType.TINY_INT_BOOL.javaType.toString())
        assertEquals("kotlin.Short", MySqlType.TINY_INT.javaType.toString())
        assertEquals("kotlin.Short", MySqlType.SMALL_INT.javaType.toString())
        assertEquals("kotlin.Int", MySqlType.INTEGER.javaType.toString())
        assertEquals("kotlin.Long", MySqlType.BIG_INT.javaType.toString())
        assertEquals("kotlin.Boolean", MySqlType.BIT.javaType.toString())
        assertEquals("kotlin.Double", MySqlType.NUMERIC.javaType.toString())
        assertEquals("kotlinx.datetime.LocalDate", MySqlType.DATE.javaType.toString())
        assertEquals("kotlinx.datetime.LocalTime", MySqlType.TIME.javaType.toString())
        assertEquals("kotlinx.datetime.LocalDateTime", MySqlType.TIMESTAMP.javaType.toString())
        assertEquals("kotlinx.datetime.LocalDateTime", MySqlType.DATETIME.javaType.toString())
    }

    @Test
    fun `datetime and timestamp use consistent kotlin types and codegen`() {
        // Regression test: DATETIME used to map to kotlin.time.Instant while binding and
        // reading through the LocalDateTime methods, producing non-compiling generated code.
        assertEquals(MySqlType.TIMESTAMP.javaType, MySqlType.DATETIME.javaType)
        assertEquals("bindLocalTimestamp(0, value)", MySqlType.DATETIME.binder())
        assertEquals("bindLocalTimestamp(0, value)", MySqlType.TIMESTAMP.binder())
        assertEquals("cursor.getLocalTimestamp(0)", MySqlType.DATETIME.getter())
        assertEquals("cursor.getLocalTimestamp(0)", MySqlType.TIMESTAMP.getter())
    }

    @Test
    fun `dialect types bind through the sqlx4k prepared statement`() {
        assertEquals("bindShort(0, value)", MySqlType.TINY_INT.binder())
        assertEquals("bindShort(0, value)", MySqlType.SMALL_INT.binder())
        assertEquals("bindInt(0, value)", MySqlType.INTEGER.binder())
        assertEquals("bindLong(0, value)", MySqlType.BIG_INT.binder())
        assertEquals("bindDouble(0, value)", MySqlType.NUMERIC.binder())
        assertEquals("bindDate(0, value)", MySqlType.DATE.binder())
        assertEquals("bindTime(0, value)", MySqlType.TIME.binder())
        assertEquals("bindLong(0, value)", MySqlType.TINY_INT_BOOL.binder())
        assertEquals("bindLong(0, value)", MySqlType.BIT.binder())
    }

    @Test
    fun `dialect types read through the sqlx4k cursor`() {
        assertEquals("cursor.getShort(0)", MySqlType.TINY_INT.getter())
        assertEquals("cursor.getShort(0)", MySqlType.SMALL_INT.getter())
        assertEquals("cursor.getInt(0)", MySqlType.INTEGER.getter())
        assertEquals("cursor.getLong(0)", MySqlType.BIG_INT.getter())
        assertEquals("cursor.getDouble(0)", MySqlType.NUMERIC.getter())
        assertEquals("cursor.getDate(0)", MySqlType.DATE.getter())
        assertEquals("cursor.getTime(0)", MySqlType.TIME.getter())
        assertEquals("cursor.getLong(0)", MySqlType.TINY_INT_BOOL.getter())
        assertEquals("cursor.getLong(0)", MySqlType.BIT.getter())
    }

    @Test
    fun `boolean-like types encode and decode through long values`() {
        assertEquals("if (value) 1L else 0L", MySqlType.TINY_INT_BOOL.encode(CodeBlock.of("value")).toString())
        assertEquals("value == 1L", MySqlType.TINY_INT_BOOL.decode(CodeBlock.of("value")).toString())
        assertEquals("if (value) 1L else 0L", MySqlType.BIT.encode(CodeBlock.of("value")).toString())
        assertEquals("value == 1L", MySqlType.BIT.decode(CodeBlock.of("value")).toString())
    }

    // ---- Remapping of upstream (JVM-only) types ----

    @Test
    fun `upstream dialect types are remapped to multiplatform types`() {
        with(resolver) {
            assertSame(MySqlType.TINY_INT, IntermediateType(upstream("TINY_INT")).remapped().dialectType)
            assertSame(MySqlType.TINY_INT_BOOL, IntermediateType(upstream("TINY_INT_BOOL")).remapped().dialectType)
            assertSame(MySqlType.SMALL_INT, IntermediateType(upstream("SMALL_INT")).remapped().dialectType)
            assertSame(MySqlType.INTEGER, IntermediateType(upstream("INTEGER")).remapped().dialectType)
            assertSame(MySqlType.BIG_INT, IntermediateType(upstream("BIG_INT")).remapped().dialectType)
            assertSame(MySqlType.BIT, IntermediateType(upstream("BIT")).remapped().dialectType)
            assertSame(MySqlType.NUMERIC, IntermediateType(upstream("NUMERIC")).remapped().dialectType)
            assertSame(MySqlType.DATE, IntermediateType(upstream("DATE")).remapped().dialectType)
            assertSame(MySqlType.TIME, IntermediateType(upstream("TIME")).remapped().dialectType)
            assertSame(MySqlType.DATETIME, IntermediateType(upstream("DATETIME")).remapped().dialectType)
            assertSame(MySqlType.TIMESTAMP, IntermediateType(upstream("TIMESTAMP")).remapped().dialectType)
        }
    }

    @Test
    fun `remapping preserves nullability`() {
        with(resolver) {
            val nullable = IntermediateType(upstream("TIMESTAMP")).asNullable().remapped()
            assertTrue(nullable.javaType.isNullable)
            assertEquals("kotlinx.datetime.LocalDateTime", nullable.javaType.copy(nullable = false).toString())

            val nonNullable = IntermediateType(upstream("TIMESTAMP")).remapped()
            assertFalse(nonNullable.javaType.isNullable)
        }
    }

    @Test
    fun `multiplatform types pass through the remapping unchanged`() {
        with(resolver) {
            val primitive = IntermediateType(PrimitiveType.TEXT)
            assertSame(primitive, primitive.remapped())

            val own = IntermediateType(MySqlType.DATETIME)
            assertSame(own, own.remapped())
        }
    }
}
