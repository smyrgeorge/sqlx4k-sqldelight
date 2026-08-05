package io.github.smyrgeorge.sqlx4k.sqldelight.dialect.sqlite

import app.cash.sqldelight.dialect.api.IntermediateType
import app.cash.sqldelight.dialect.api.QueryWithResults
import app.cash.sqldelight.dialect.api.TypeResolver
import app.cash.sqldelight.dialects.sqlite_3_38.SqliteTypeResolver
import com.alecstrong.sql.psi.core.psi.SqlExpr
import com.alecstrong.sql.psi.core.psi.SqlFunctionExpr
import com.alecstrong.sql.psi.core.psi.SqlStmt
import com.alecstrong.sql.psi.core.psi.SqlTypeName
import com.intellij.psi.PsiElement
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Tests for the sqlx4k SQLite dialect configuration.
 *
 * Unlike the PostgreSQL and MySQL dialects there is no custom type resolver: the SQLite type
 * resolvers only produce SqlDelight's primitive types, which are multiplatform-safe. The full
 * `.sq`-to-Kotlin code generation is exercised by the `examples:sqlite-sqldelight` module,
 * which generates and compiles code with this dialect on every build.
 */
class SqliteSqlx4kDialectTests {

    private val dialect = SqliteSqlx4kDialect()

    private val parentResolver = object : TypeResolver {
        override fun resolvedType(expr: SqlExpr): IntermediateType = error("Not used")
        override fun argumentType(parent: PsiElement, argument: SqlExpr): IntermediateType = error("Not used")
        override fun functionType(functionExpr: SqlFunctionExpr): IntermediateType? = error("Not used")
        override fun definitionType(typeName: SqlTypeName): IntermediateType = error("Not used")
        override fun queryWithResults(sqlStmt: SqlStmt): QueryWithResults? = error("Not used")
    }

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
    fun `dialect is a sqlite dialect`() {
        assertTrue(dialect.isSqlite)
    }

    @Test
    fun `type resolver is the upstream sqlite resolver`() {
        // The upstream resolver only produces multiplatform-safe primitive types,
        // so it is used as-is without remapping.
        assertIs<SqliteTypeResolver>(dialect.typeResolver(parentResolver))
    }
}
