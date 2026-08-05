package io.github.smyrgeorge.sqlx4k.sqldelight

import io.github.smyrgeorge.sqlx4k.Dialect
import io.github.smyrgeorge.sqlx4k.SQLError
import io.github.smyrgeorge.sqlx4k.Statement
import io.github.smyrgeorge.sqlx4k.ValueEncoderRegistry
import io.github.smyrgeorge.sqlx4k.impl.types.TypedNull
import kotlinx.datetime.DateTimePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.time.Instant
import kotlin.uuid.Uuid

class SqlDelightPreparedStatementTests {

    private fun SqlDelightPreparedStatement.render(): Statement.NativeQuery =
        statement.renderNativeQuery(Dialect.PostgreSQL, ValueEncoderRegistry.EMPTY)

    /** Renders a single-parameter statement and returns the single native value. */
    private fun singleValue(binder: SqlDelightPreparedStatement.() -> Unit): Any? =
        SqlDelightPreparedStatement("SELECT $1").apply(binder).render().values.single()

    /** Asserts that the single native value is a [TypedNull] of the given type. */
    private fun assertTypedNull(type: KClass<*>, binder: SqlDelightPreparedStatement.() -> Unit) {
        val value = assertIs<TypedNull>(singleValue(binder))
        assertEquals(type, value.type)
    }

    // ---- Non-null values are bound as-is (no local rendering) ----

    @Test
    fun `bindBoolean binds the raw value`() {
        assertEquals(true, singleValue { bindBoolean(0, true) })
        assertEquals(false, singleValue { bindBoolean(0, false) })
    }

    @Test
    fun `bindBytes binds the raw byte array`() {
        val bytes = byteArrayOf(1, 2, 3, -1)
        assertSame(bytes, singleValue { bindBytes(0, bytes) })
    }

    @Test
    fun `bindDouble binds the raw value`() {
        assertEquals(3.14, singleValue { bindDouble(0, 3.14) })
    }

    @Test
    fun `bindShort binds the raw value`() {
        assertEquals(7.toShort(), singleValue { bindShort(0, 7) })
    }

    @Test
    fun `bindInt binds the raw value`() {
        assertEquals(42, singleValue { bindInt(0, 42) })
    }

    @Test
    fun `bindLong binds the raw value`() {
        assertEquals(Long.MAX_VALUE, singleValue { bindLong(0, Long.MAX_VALUE) })
    }

    @Test
    fun `bindString binds the raw value`() {
        assertEquals("it's a test", singleValue { bindString(0, "it's a test") })
    }

    @Test
    fun `bindDate binds the raw LocalDate`() {
        val date = LocalDate(2023, 6, 15)
        assertEquals(date, singleValue { bindDate(0, date) })
    }

    @Test
    fun `bindTime binds the raw LocalTime`() {
        val time = LocalTime(14, 30, 45)
        assertEquals(time, singleValue { bindTime(0, time) })
    }

    @Test
    fun `bindLocalTimestamp binds the raw LocalDateTime`() {
        val timestamp = LocalDateTime(2023, 6, 15, 14, 30, 45)
        assertEquals(timestamp, singleValue { bindLocalTimestamp(0, timestamp) })
    }

    @Test
    fun `bindTimestamp binds the raw Instant`() {
        val instant = Instant.parse("2023-06-15T14:30:45Z")
        assertEquals(instant, singleValue { bindTimestamp(0, instant) })
    }

    @Test
    fun `bindInterval binds the ISO-8601 string representation`() {
        val period = DateTimePeriod(days = 1, hours = 2, minutes = 30)
        assertEquals(period.toString(), singleValue { bindInterval(0, period) })
    }

    @Test
    fun `bindUuid binds the raw Uuid`() {
        val uuid = Uuid.parse("d9b9c2a0-0e29-4c5f-9e6a-3d0a3b1c2d4e")
        assertEquals(uuid, singleValue { bindUuid(0, uuid) })
    }

    // ---- Null values are bound as typed nulls ----

    @Test
    fun `null values are bound as typed nulls`() {
        assertTypedNull(Boolean::class) { bindBoolean(0, null) }
        assertTypedNull(ByteArray::class) { bindBytes(0, null) }
        assertTypedNull(Double::class) { bindDouble(0, null) }
        assertTypedNull(Short::class) { bindShort(0, null) }
        assertTypedNull(Int::class) { bindInt(0, null) }
        assertTypedNull(Long::class) { bindLong(0, null) }
        assertTypedNull(String::class) { bindString(0, null) }
        assertTypedNull(LocalDate::class) { bindDate(0, null) }
        assertTypedNull(LocalTime::class) { bindTime(0, null) }
        assertTypedNull(LocalDateTime::class) { bindLocalTimestamp(0, null) }
        assertTypedNull(Instant::class) { bindTimestamp(0, null) }
        assertTypedNull(Uuid::class) { bindUuid(0, null) }
        // Intervals are bound as text, so the typed null is a String null.
        assertTypedNull(String::class) { bindInterval(0, null) }
    }

    // ---- Multiple parameters ----

    @Test
    fun `multiple parameters are collected in placeholder order`() {
        val prepared = SqlDelightPreparedStatement(
            "INSERT INTO users (id, name, age, active) VALUES ($1, $2, $3, $4)"
        ).apply {
            bindLong(0, 1)
            bindString(1, "Alice")
            bindInt(2, 30)
            bindBoolean(3, true)
        }

        val query = prepared.render()
        assertEquals("INSERT INTO users (id, name, age, active) VALUES ($1, $2, $3, $4)", query.sql)
        assertEquals(listOf<Any?>(1L, "Alice", 30, true), query.values)
    }

    @Test
    fun `binding out of bounds fails`() {
        val prepared = SqlDelightPreparedStatement("SELECT $1")
        val exception = assertFailsWith<SQLError> { prepared.bindString(1, "value") }
        assertEquals(SQLError.Code.PositionalParameterOutOfBounds, exception.code)
    }

    // ---- Question-mark placeholders (e.g. SQLDelight MySQL dialect) ----

    @Test
    fun `question mark placeholders are bound through the base statement`() {
        val prepared = SqlDelightPreparedStatement(
            "INSERT INTO users (id, name) VALUES (?, ?)"
        ).apply {
            bindLong(0, 1)
            bindString(1, "Alice")
        }

        val query = prepared.statement.renderNativeQuery(Dialect.MySQL, ValueEncoderRegistry.EMPTY)
        assertEquals("INSERT INTO users (id, name) VALUES (?, ?)", query.sql)
        assertEquals(listOf<Any?>(1L, "Alice"), query.values)
    }

    @Test
    fun `question mark placeholders support typed nulls`() {
        val prepared = SqlDelightPreparedStatement(
            "INSERT INTO users (id, name) VALUES (?, ?)"
        ).apply {
            bindLong(0, 1)
            bindString(1, null)
        }

        val query = prepared.statement.renderNativeQuery(Dialect.MySQL, ValueEncoderRegistry.EMPTY)
        assertEquals(1L, query.values[0])
        val value = assertIs<TypedNull>(query.values[1])
        assertEquals(String::class, value.type)
    }
}
