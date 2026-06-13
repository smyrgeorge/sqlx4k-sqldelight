package io.github.smyrgeorge.sqlx4k.sqldelight

import io.github.smyrgeorge.sqlx4k.ResultSet
import io.github.smyrgeorge.sqlx4k.SQLError
import io.github.smyrgeorge.sqlx4k.ValueEncoder
import io.github.smyrgeorge.sqlx4k.ValueEncoderRegistry
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class ExtendedStatementTests {

    @Test
    fun `Basic test for PostgreSQL-style positional parameters with integers`() {
        val sql = "SELECT * FROM users WHERE id > $1 AND id < $2"
        val res = ExtendedStatement(sql)
            .bind(0, 65)
            .bind(1, 66)
            .render()

        assertContains(res, "id > 65")
        assertContains(res, "id < 66")
    }

    @Test
    fun `Basic test for PostgreSQL-style positional parameters with strings`() {
        val sql = "SELECT * FROM users WHERE username = $1"
        val res = ExtendedStatement(sql)
            .bind(0, "test_user")
            .render()

        assertContains(res, "username = 'test_user'")
    }

    @Test
    fun `Binding PostgreSQL-style positional parameters out of order`() {
        val sql = "SELECT * FROM users WHERE id > $1 AND id < $2"
        val res = ExtendedStatement(sql)
            .bind(1, 66)
            .bind(0, 65)
            .render()

        assertContains(res, "id > 65")
        assertContains(res, "id < 66")
    }

    @Test
    fun `PostgreSQL-style positional parameters with null values`() {
        val sql = "SELECT * FROM users WHERE id = $1 OR username = $2"
        val res = ExtendedStatement(sql)
            .bind(0, 123)
            .bind(1, null)
            .render()

        assertEquals("SELECT * FROM users WHERE id = 123 OR username = null", res)
    }

    @Test
    fun `PostgreSQL-style positional parameters with boolean values`() {
        val sql = "SELECT * FROM users WHERE is_active = $1 AND is_verified = $2"
        val res = ExtendedStatement(sql)
            .bind(0, true)
            .bind(1, false)
            .render()

        assertEquals("SELECT * FROM users WHERE is_active = true AND is_verified = false", res)
    }

    @Test
    fun `PostgreSQL-style positional parameters with empty strings`() {
        val sql = "SELECT * FROM users WHERE name = $1 OR bio = $2"
        val res = ExtendedStatement(sql)
            .bind(0, "")
            .bind(1, "   ")
            .render()

        assertEquals("SELECT * FROM users WHERE name = '' OR bio = '   '", res)
    }

    @Test
    fun `PostgreSQL-style positional parameters - binding the same index multiple times should override`() {
        val sql = "SELECT * FROM users WHERE id = $1"
        val res = ExtendedStatement(sql)
            .bind(0, 123)
            .bind(0, 456) // Should override the previous value
            .render()

        assertEquals("SELECT * FROM users WHERE id = 456", res)
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
            statement.render()
        }

        assertEquals(SQLError.Code.PositionalParameterValueNotSupplied, exception.code)
    }

    @Test
    fun `PostgreSQL-style positional parameters - SQL injection prevention`() {
        val sql = "SELECT * FROM users WHERE username = $1"
        val maliciousUsername = "admin'; DROP TABLE users; --"

        val res = ExtendedStatement(sql)
            .bind(0, maliciousUsername)
            .render()

        assertContains(res, "username = 'admin''; DROP TABLE users; --'")
        assertFalse(res.contains("username = 'admin'; DROP TABLE users; --'"))
    }

    @Test
    fun `PostgreSQL-style positional parameters - multiple parameters with various types`() {
        val sql = "INSERT INTO users (username, age, is_admin, created_at) VALUES ($1, $2, $3, $4)"
        val username = "test_user"
        val age = 25
        val isAdmin = false
        val createdAt = "2023-01-01"

        val res = ExtendedStatement(sql)
            .bind(0, username)
            .bind(1, age)
            .bind(2, isAdmin)
            .bind(3, createdAt)
            .render()

        assertContains(res, "VALUES ('test_user'")
        assertContains(res, "25")
        assertContains(res, "false")
        assertContains(res, "'2023-01-01'")
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
        val customValue = CustomType("custom_value")

        val res = ExtendedStatement(sql)
            .bind(0, customValue)
            .render(encoders)

        assertContains(res, "custom_field = 'custom_value'")
    }

    @Test
    fun `PostgreSQL-style positional parameters with special characters in strings`() {
        val sql = "SELECT * FROM users WHERE message = $1"
        val res = ExtendedStatement(sql)
            .bind(0, "Message with 'quotes' and \"double quotes\" and \\ backslashes")
            .render()

        assertContains(res, "message = 'Message with ''quotes'' and \"double quotes\" and \\ backslashes'")
    }

    @Test
    fun `PostgreSQL-style positional parameters with decimal numbers`() {
        val sql = "SELECT * FROM products WHERE price > $1 AND weight < $2"
        val res = ExtendedStatement(sql)
            .bind(0, 19.99)
            .bind(1, 2.5)
            .render()

        assertContains(res, "price > 19.99")
        assertContains(res, "weight < 2.5")
    }

    @Test
    fun `PostgreSQL-style positional parameters with large numbers`() {
        val sql = "SELECT * FROM metrics WHERE value = $1"
        val res = ExtendedStatement(sql)
            .bind(0, 9223372036854775807L) // Long.MAX_VALUE
            .render()

        assertContains(res, "value = 9223372036854775807")
    }

    @Test
    fun `PostgreSQL-style positional parameters with repeated parameters`() {
        val sql = "SELECT * FROM users WHERE id = $1 OR parent_id = $1"
        val res = ExtendedStatement(sql)
            .bind(0, 123)
            .render()

        assertEquals("SELECT * FROM users WHERE id = 123 OR parent_id = 123", res)
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
            .render()

        assertEquals("SELECT * FROM users WHERE id IN (1, 2, 3, 4, 5)", res)
    }

    @Test
    fun `PostgreSQL-style positional parameters with dates or timestamps`() {
        val sql = "SELECT * FROM events WHERE created_at > $1"
        val res = ExtendedStatement(sql)
            .bind(0, "2023-06-15T14:30:00Z")
            .render()

        assertContains(res, "created_at > '2023-06-15T14:30:00Z'")
    }

    @Test
    fun `PostgreSQL-style positional parameters inside complex expressions`() {
        val sql = "SELECT * FROM users WHERE (age BETWEEN $1 AND $2) OR (salary > $3 AND department = $4)"
        val res = ExtendedStatement(sql)
            .bind(0, 25)
            .bind(1, 45)
            .bind(2, 50000)
            .bind(3, "Engineering")
            .render()

        assertContains(res, "age BETWEEN 25 AND 45")
        assertContains(res, "salary > 50000")
        assertContains(res, "department = 'Engineering'")
    }

    @Test
    fun `PostgreSQL-style positional parameters - render without binding`() {
        val sql = "SELECT * FROM users WHERE id = $1"
        val statement = ExtendedStatement(sql)

        val exception = assertFailsWith<SQLError> {
            statement.render() // No parameters bound
        }

        assertEquals(SQLError.Code.PositionalParameterValueNotSupplied, exception.code)
    }

    // ---- SQL contexts: comments, quotes, and dollar-quoted strings ----

    @Test
    fun `placeholders in line comments are not replaced`() {
        val sql = """
            select 1 -- comment with ? and :name and $1
            , 2 as two
        """.trimIndent()
        val rendered = ExtendedStatement(sql).render()
        assertContains(rendered, "-- comment with ? and :name and $1")
    }

    @Test
    fun `placeholders in block comments are not replaced`() {
        val sql = """
            /* block with placeholders $2 */
            select $2 as pg
        """.trimIndent()
        val rendered = ExtendedStatement(sql)
            .bind(1, 5)
            .render()
        assertContains(rendered, "/* block with placeholders $2 */")
        assertContains(rendered, "select 5 as pg")
    }

    @Test
    fun `placeholders inside dollar-quoted strings are not replaced`() {
        val sql = """
            select $$ body with ? and :name and $3 $$ as txt, $3 as pg
        """.trimIndent()
        val rendered = ExtendedStatement(sql).bind(2, 11).render()
        assertContains(rendered, "$$ body with ? and :name and $3 $$")
        assertContains(rendered, "11 as pg")
    }

    @Test
    fun `extended statement supports high indices like dollar10`() {
        val sql = "select $10 as v"
        val rendered = ExtendedStatement(sql).bind(9, 123).render()
        assertContains(rendered, "select 123 as v")
    }

    @Test
    fun `extended statement errors when used parameter missing`() {
        val sql = "select $2 as v"
        val ex = assertFailsWith<SQLError> {
            ExtendedStatement(sql).render()
        }
        assertEquals(SQLError.Code.PositionalParameterValueNotSupplied, ex.code)
    }

    @Test
    fun `dollar-sign followed by digits is not treated as a dollar tag`() {
        // $1$ should NOT be recognized as a dollar-quoted string delimiter.
        // PostgreSQL requires dollar tag identifiers to start with a letter or underscore.
        val sql = "select $1 as a, $2 as b"
        val rendered = ExtendedStatement(sql)
            .bind(0, 10)
            .bind(1, 20)
            .render()
        assertEquals("select 10 as a, 20 as b", rendered)
    }

    @Test
    fun `dollar-sign with digit-starting tag does not swallow SQL`() {
        // If $1$ were mistakenly treated as a dollar tag, the scanner would enter
        // dollar-quoted mode and swallow everything until the next $1$.
        val sql = "select $1, $2 from t"
        val rendered = ExtendedStatement(sql)
            .bind(0, "a")
            .bind(1, "b")
            .render()
        assertEquals("select 'a', 'b' from t", rendered)
    }

    @Test
    fun `nested block comments with extended statement`() {
        val sql = """
            /* outer /* $1 */ still comment */ select $1 as val
        """.trimIndent()
        val rendered = ExtendedStatement(sql)
            .bind(0, 99)
            .render()
        assertContains(rendered, "/* outer /* $1 */ still comment */")
        assertContains(rendered, "select 99 as val")
    }
}
