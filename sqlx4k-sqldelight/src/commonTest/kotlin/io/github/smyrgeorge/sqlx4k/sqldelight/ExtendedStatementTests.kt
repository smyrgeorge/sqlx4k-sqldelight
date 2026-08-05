package io.github.smyrgeorge.sqlx4k.sqldelight

import io.github.smyrgeorge.sqlx4k.Dialect
import io.github.smyrgeorge.sqlx4k.ResultSet
import io.github.smyrgeorge.sqlx4k.SQLError
import io.github.smyrgeorge.sqlx4k.ValueEncoder
import io.github.smyrgeorge.sqlx4k.ValueEncoderRegistry
import io.github.smyrgeorge.sqlx4k.impl.types.TypedNull
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class ExtendedStatementTests {

    private fun ExtendedStatement.renderPostgres(encoders: ValueEncoderRegistry = ValueEncoderRegistry.EMPTY) =
        renderNativeQuery(Dialect.PostgreSQL, encoders)

    private fun ExtendedStatement.renderMySql(encoders: ValueEncoderRegistry = ValueEncoderRegistry.EMPTY) =
        renderNativeQuery(Dialect.MySQL, encoders)

    @Test
    fun `Basic test for PostgreSQL-style positional parameters with integers`() {
        val sql = "SELECT * FROM users WHERE id > $1 AND id < $2"
        val res = ExtendedStatement(sql)
            .bind(0, 65)
            .bind(1, 66)
            .renderPostgres()

        assertEquals("SELECT * FROM users WHERE id > $1 AND id < $2", res.sql)
        assertEquals(listOf<Any?>(65, 66), res.values)
    }

    @Test
    fun `Values are not rendered into the SQL string`() {
        val sql = "SELECT * FROM users WHERE username = $1"
        val res = ExtendedStatement(sql)
            .bind(0, "test_user")
            .renderPostgres()

        assertEquals("SELECT * FROM users WHERE username = $1", res.sql)
        assertEquals(listOf<Any?>("test_user"), res.values)
    }

    @Test
    fun `Binding PostgreSQL-style positional parameters out of order`() {
        val sql = "SELECT * FROM users WHERE id > $1 AND id < $2"
        val res = ExtendedStatement(sql)
            .bind(1, 66)
            .bind(0, 65)
            .renderPostgres()

        assertEquals(listOf<Any?>(65, 66), res.values)
    }

    @Test
    fun `PostgreSQL-style positional parameters with null values`() {
        val sql = "SELECT * FROM users WHERE id = $1 OR username = $2"
        val res = ExtendedStatement(sql)
            .bind(0, 123)
            .bind(1, null)
            .renderPostgres()

        assertEquals(listOf<Any?>(123, null), res.values)
    }

    @Test
    fun `PostgreSQL-style positional parameters with typed null values`() {
        val sql = "SELECT * FROM users WHERE username = $1"
        val res = ExtendedStatement(sql)
            .bindNull(0, String::class)
            .renderPostgres()

        val value = assertIs<TypedNull>(res.values.single())
        assertEquals(String::class, value.type)
    }

    @Test
    fun `PostgreSQL-style positional parameters - binding the same index multiple times should override`() {
        val sql = "SELECT * FROM users WHERE id = $1"
        val res = ExtendedStatement(sql)
            .bind(0, 123)
            .bind(0, 456) // Should override the previous value
            .renderPostgres()

        assertEquals(listOf<Any?>(456), res.values)
    }

    @Test
    fun `PostgreSQL-style positional parameters - index out of bounds exception`() {
        val sql = "SELECT * FROM users WHERE id = $1"
        val statement = ExtendedStatement(sql)

        val exception = assertFailsWith<SQLError> {
            statement.bind(1, "value") // Index 1 is out of bounds (should be 0)
        }

        assertEquals(SQLError.Code.PositionalParameterOutOfBounds, exception.code)
    }

    @Test
    fun `PostgreSQL-style positional parameters - missing parameter value exception`() {
        val sql = "SELECT * FROM users WHERE id = $1 AND username = $2"
        val statement = ExtendedStatement(sql)
            .bind(0, 123)
        // Not binding the second parameter

        val exception = assertFailsWith<SQLError> {
            statement.renderPostgres()
        }

        assertEquals(SQLError.Code.PositionalParameterValueNotSupplied, exception.code)
    }

    @Test
    fun `PostgreSQL-style positional parameters - multiple parameters with various types`() {
        val sql = "INSERT INTO users (username, age, is_admin, created_at) VALUES ($1, $2, $3, $4)"
        val res = ExtendedStatement(sql)
            .bind(0, "test_user")
            .bind(1, 25)
            .bind(2, false)
            .bind(3, "2023-01-01")
            .renderPostgres()

        assertEquals("INSERT INTO users (username, age, is_admin, created_at) VALUES ($1, $2, $3, $4)", res.sql)
        assertEquals(listOf<Any?>("test_user", 25, false, "2023-01-01"), res.values)
    }

    @Test
    fun `PostgreSQL-style positional parameters - with custom value encoder`() {
        class CustomType(val value: String)
        class CustomTypeEncoder : ValueEncoder<CustomType> {
            override fun encode(value: CustomType): Any = value.value
            override fun decode(value: ResultSet.Row.Column): CustomType = CustomType(value.asString())
        }

        val encoders = ValueEncoderRegistry()
            .register(CustomTypeEncoder())

        val sql = "SELECT * FROM data WHERE custom_field = $1"
        val res = ExtendedStatement(sql)
            .bind(0, CustomType("custom_value"))
            .renderPostgres(encoders)

        assertEquals(listOf<Any?>("custom_value"), res.values)
    }

    @Test
    fun `PostgreSQL-style positional parameters with repeated parameters`() {
        val sql = "SELECT * FROM users WHERE id = $1 OR parent_id = $1"
        val res = ExtendedStatement(sql)
            .bind(0, 123)
            .renderPostgres()

        // The value is collected once per occurrence and placeholders are renumbered sequentially.
        assertEquals("SELECT * FROM users WHERE id = $1 OR parent_id = $2", res.sql)
        assertEquals(listOf<Any?>(123, 123), res.values)
    }

    @Test
    fun `PostgreSQL-style positional parameters with consecutive parameters`() {
        val sql = "SELECT * FROM users WHERE id IN ($1, $2, $3, $4, $5)"
        val res = ExtendedStatement(sql)
            .bind(0, 1)
            .bind(1, 2)
            .bind(2, 3)
            .bind(3, 4)
            .bind(4, 5)
            .renderPostgres()

        assertEquals("SELECT * FROM users WHERE id IN ($1, $2, $3, $4, $5)", res.sql)
        assertEquals(listOf<Any?>(1, 2, 3, 4, 5), res.values)
    }

    @Test
    fun `PostgreSQL-style positional parameters - render without binding`() {
        val sql = "SELECT * FROM users WHERE id = $1"
        val statement = ExtendedStatement(sql)

        val exception = assertFailsWith<SQLError> {
            statement.renderPostgres() // No parameters bound
        }

        assertEquals(SQLError.Code.PositionalParameterValueNotSupplied, exception.code)
    }

    @Test
    fun `Statement without parameters renders unchanged`() {
        val sql = "SELECT * FROM users"
        val res = ExtendedStatement(sql).renderPostgres()

        assertEquals(sql, res.sql)
        assertEquals(emptyList(), res.values)
    }

    // ---- MySQL dialect rendering of PostgreSQL-style placeholders ----

    @Test
    fun `MySQL dialect rewrites dollar placeholders to question marks`() {
        val sql = "SELECT * FROM users WHERE id > $1 AND id < $2"
        val res = ExtendedStatement(sql)
            .bind(0, 65)
            .bind(1, 66)
            .renderMySql()

        assertEquals("SELECT * FROM users WHERE id > ? AND id < ?", res.sql)
        assertEquals(listOf<Any?>(65, 66), res.values)
    }

    @Test
    fun `MySQL dialect duplicates values for repeated placeholders`() {
        val sql = "SELECT * FROM users WHERE id = $1 OR parent_id = $1"
        val res = ExtendedStatement(sql)
            .bind(0, 123)
            .renderMySql()

        assertEquals("SELECT * FROM users WHERE id = ? OR parent_id = ?", res.sql)
        assertEquals(listOf<Any?>(123, 123), res.values)
    }

    // ---- Base '?' positional parameters (e.g. SQLDelight MySQL dialect) ----

    @Test
    fun `question mark placeholders are bound through the base statement`() {
        val sql = "INSERT INTO users (id, name) VALUES (?, ?)"
        val res = ExtendedStatement(sql)
            .bind(0, 1)
            .bind(1, "John")
            .renderMySql()

        assertEquals("INSERT INTO users (id, name) VALUES (?, ?)", res.sql)
        assertEquals(listOf<Any?>(1, "John"), res.values)
    }

    @Test
    fun `question mark placeholders render to dollar placeholders for PostgreSQL`() {
        val sql = "INSERT INTO users (id, name) VALUES (?, ?)"
        val res = ExtendedStatement(sql)
            .bind(0, 1)
            .bind(1, "John")
            .renderPostgres()

        assertEquals("INSERT INTO users (id, name) VALUES ($1, $2)", res.sql)
        assertEquals(listOf<Any?>(1, "John"), res.values)
    }

    @Test
    fun `question mark placeholders support typed nulls`() {
        val sql = "INSERT INTO users (id, name) VALUES (?, ?)"
        val res = ExtendedStatement(sql)
            .bind(0, 1)
            .bindNull(1, String::class)
            .renderMySql()

        assertEquals(1, res.values[0])
        val value = assertIs<TypedNull>(res.values[1])
        assertEquals(String::class, value.type)
    }

    // ---- SQL contexts: comments, quotes, and dollar-quoted strings ----

    @Test
    fun `placeholders in line comments are not replaced`() {
        val sql = """
            select 1 -- comment with ? and :name and $1
            , 2 as two
        """.trimIndent()
        val res = ExtendedStatement(sql).renderPostgres()
        assertContains(res.sql, "-- comment with ? and :name and $1")
        assertEquals(emptyList(), res.values)
    }

    @Test
    fun `placeholders in block comments are not replaced`() {
        val sql = """
            /* block with placeholders $2 */
            select $2 as pg
        """.trimIndent()
        val res = ExtendedStatement(sql)
            .bind(1, 5)
            .renderPostgres()
        assertContains(res.sql, "/* block with placeholders $2 */")
        assertContains(res.sql, "select $1 as pg")
        assertEquals(listOf<Any?>(5), res.values)
    }

    @Test
    fun `placeholders inside dollar-quoted strings are not replaced`() {
        val sql = """
            select $$ body with ? and :name and $3 $$ as txt, $3 as pg
        """.trimIndent()
        val res = ExtendedStatement(sql).bind(2, 11).renderPostgres()
        assertContains(res.sql, "$$ body with ? and :name and $3 $$")
        assertContains(res.sql, "$1 as pg")
        assertEquals(listOf<Any?>(11), res.values)
    }

    @Test
    fun `extended statement supports high indices like dollar10`() {
        val sql = "select $10 as v"
        val res = ExtendedStatement(sql).bind(9, 123).renderPostgres()
        assertEquals("select $1 as v", res.sql)
        assertEquals(listOf<Any?>(123), res.values)
    }

    @Test
    fun `extended statement errors when used parameter missing`() {
        val sql = "select $2 as v"
        val ex = assertFailsWith<SQLError> {
            ExtendedStatement(sql).renderPostgres()
        }
        assertEquals(SQLError.Code.PositionalParameterValueNotSupplied, ex.code)
    }

    @Test
    fun `dollar-sign followed by digits is not treated as a dollar tag`() {
        // $1$ should NOT be recognized as a dollar-quoted string delimiter.
        // PostgreSQL requires dollar tag identifiers to start with a letter or underscore.
        val sql = "select $1 as a, $2 as b"
        val res = ExtendedStatement(sql)
            .bind(0, 10)
            .bind(1, 20)
            .renderPostgres()
        assertEquals("select $1 as a, $2 as b", res.sql)
        assertEquals(listOf<Any?>(10, 20), res.values)
    }

    @Test
    fun `dollar-sign with digit-starting tag does not swallow SQL`() {
        // If $1$ were mistakenly treated as a dollar tag, the scanner would enter
        // dollar-quoted mode and swallow everything until the next $1$.
        val sql = "select $1, $2 from t"
        val res = ExtendedStatement(sql)
            .bind(0, "a")
            .bind(1, "b")
            .renderPostgres()
        assertEquals("select $1, $2 from t", res.sql)
        assertEquals(listOf<Any?>("a", "b"), res.values)
    }

    @Test
    fun `nested block comments with extended statement`() {
        val sql = """
            /* outer /* $1 */ still comment */ select $1 as val
        """.trimIndent()
        val res = ExtendedStatement(sql)
            .bind(0, 99)
            .renderPostgres()
        assertContains(res.sql, "/* outer /* $1 */ still comment */")
        assertContains(res.sql, "select $1 as val")
        assertEquals(listOf<Any?>(99), res.values)
    }
}
