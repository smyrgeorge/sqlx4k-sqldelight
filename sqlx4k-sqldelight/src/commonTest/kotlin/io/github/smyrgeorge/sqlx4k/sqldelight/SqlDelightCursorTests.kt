package io.github.smyrgeorge.sqlx4k.sqldelight

import io.github.smyrgeorge.sqlx4k.ResultSet
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.DateTimePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlin.uuid.Uuid

class SqlDelightCursorTests {

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

    // ---- Row iteration ----

    @Test
    fun `next returns false for an empty result set`() = runTest {
        val cursor = SqlDelightCursor(resultSetOf())
        assertFalse(cursor.next().await())
    }

    @Test
    fun `next advances through all rows and then returns false`() = runTest {
        val cursor = SqlDelightCursor(resultSetOf(row("a"), row("b"), row("c")))

        assertTrue(cursor.next().await())
        assertEquals("a", cursor.getString(0))
        assertTrue(cursor.next().await())
        assertEquals("b", cursor.getString(0))
        assertTrue(cursor.next().await())
        assertEquals("c", cursor.getString(0))
        assertFalse(cursor.next().await())
    }

    @Test
    fun `columns are accessed by index`() = runTest {
        val cursor = SqlDelightCursor(resultSetOf(row("first", "second", "third")))
        cursor.next().await()

        assertEquals("first", cursor.getString(0))
        assertEquals("second", cursor.getString(1))
        assertEquals("third", cursor.getString(2))
    }

    // ---- Value decoding ----

    @Test
    fun `getString returns the raw value or null`() = runTest {
        val cursor = SqlDelightCursor(resultSetOf(row("hello", null)))
        cursor.next().await()

        assertEquals("hello", cursor.getString(0))
        assertNull(cursor.getString(1))
    }

    @Test
    fun `getBoolean parses driver boolean representations`() = runTest {
        val cursor = SqlDelightCursor(
            resultSetOf(row("true", "t", "1", "false", "f", "0", null))
        )
        cursor.next().await()

        assertEquals(true, cursor.getBoolean(0))
        assertEquals(true, cursor.getBoolean(1))
        assertEquals(true, cursor.getBoolean(2))
        assertEquals(false, cursor.getBoolean(3))
        assertEquals(false, cursor.getBoolean(4))
        assertEquals(false, cursor.getBoolean(5))
        assertNull(cursor.getBoolean(6))
    }

    @Test
    fun `getBoolean fails on unrecognized values`() = runTest {
        val cursor = SqlDelightCursor(resultSetOf(row("yes")))
        cursor.next().await()

        assertFailsWith<IllegalStateException> { cursor.getBoolean(0) }
    }

    @Test
    fun `getDouble parses the value or returns null`() = runTest {
        val cursor = SqlDelightCursor(resultSetOf(row("3.14", null)))
        cursor.next().await()

        assertEquals(3.14, cursor.getDouble(0))
        assertNull(cursor.getDouble(1))
    }

    @Test
    fun `getShort parses the value or returns null`() = runTest {
        val cursor = SqlDelightCursor(resultSetOf(row("7", null)))
        cursor.next().await()

        assertEquals(7.toShort(), cursor.getShort(0))
        assertNull(cursor.getShort(1))
    }

    @Test
    fun `getInt parses the value or returns null`() = runTest {
        val cursor = SqlDelightCursor(resultSetOf(row("42", null)))
        cursor.next().await()

        assertEquals(42, cursor.getInt(0))
        assertNull(cursor.getInt(1))
    }

    @Test
    fun `getLong parses the value or returns null`() = runTest {
        val cursor = SqlDelightCursor(resultSetOf(row("9223372036854775807", null)))
        cursor.next().await()

        assertEquals(Long.MAX_VALUE, cursor.getLong(0))
        assertNull(cursor.getLong(1))
    }

    @Test
    fun `getBytes decodes PostgreSQL-style hex values`() = runTest {
        val cursor = SqlDelightCursor(resultSetOf(row("\\x0102ff")))
        cursor.next().await()

        assertContentEquals(byteArrayOf(1, 2, -1), cursor.getBytes(0))
    }

    @Test
    fun `getBytes decodes plain hex values`() = runTest {
        val cursor = SqlDelightCursor(resultSetOf(row("0a0b0c")))
        cursor.next().await()

        assertContentEquals(byteArrayOf(10, 11, 12), cursor.getBytes(0))
    }

    @Test
    fun `getBytes returns null for null values`() = runTest {
        val cursor = SqlDelightCursor(resultSetOf(row(null as String?)))
        cursor.next().await()

        assertNull(cursor.getBytes(0))
    }

    @Test
    fun `getBytes fails on non-hex values`() = runTest {
        val cursor = SqlDelightCursor(resultSetOf(row("not-hex")))
        cursor.next().await()

        assertFailsWith<IllegalArgumentException> { cursor.getBytes(0) }
    }

    @Test
    fun `getDate parses the value or returns null`() = runTest {
        val cursor = SqlDelightCursor(resultSetOf(row("2023-06-15", null)))
        cursor.next().await()

        assertEquals(LocalDate(2023, 6, 15), cursor.getDate(0))
        assertNull(cursor.getDate(1))
    }

    @Test
    fun `getTime parses the value or returns null`() = runTest {
        val cursor = SqlDelightCursor(resultSetOf(row("14:30:45", null)))
        cursor.next().await()

        assertEquals(LocalTime(14, 30, 45), cursor.getTime(0))
        assertNull(cursor.getTime(1))
    }

    @Test
    fun `getLocalTimestamp parses ISO and space-separated timestamps`() = runTest {
        val cursor = SqlDelightCursor(resultSetOf(row("2023-06-15T14:30:45", "2023-06-15 14:30:45", null)))
        cursor.next().await()

        val expected = LocalDateTime(2023, 6, 15, 14, 30, 45)
        assertEquals(expected, cursor.getLocalTimestamp(0))
        assertEquals(expected, cursor.getLocalTimestamp(1))
        assertNull(cursor.getLocalTimestamp(2))
    }

    @Test
    fun `getTimestamp parses ISO and space-separated instants`() = runTest {
        val cursor = SqlDelightCursor(resultSetOf(row("2023-06-15T14:30:45Z", "2023-06-15 14:30:45Z", null)))
        cursor.next().await()

        val expected = Instant.parse("2023-06-15T14:30:45Z")
        assertEquals(expected, cursor.getTimestamp(0))
        assertEquals(expected, cursor.getTimestamp(1))
        assertNull(cursor.getTimestamp(2))
    }

    @Test
    fun `getInterval parses the value or returns null`() = runTest {
        val cursor = SqlDelightCursor(resultSetOf(row("P1DT2H30M", null)))
        cursor.next().await()

        assertEquals(DateTimePeriod(days = 1, hours = 2, minutes = 30), cursor.getInterval(0))
        assertNull(cursor.getInterval(1))
    }

    @Test
    fun `getUuid parses the value or returns null`() = runTest {
        val uuid = "d9b9c2a0-0e29-4c5f-9e6a-3d0a3b1c2d4e"
        val cursor = SqlDelightCursor(resultSetOf(row(uuid, null)))
        cursor.next().await()

        assertEquals(Uuid.parse(uuid), cursor.getUuid(0))
        assertNull(cursor.getUuid(1))
    }

    @Test
    fun `values are read per row while iterating`() = runTest {
        val cursor = SqlDelightCursor(resultSetOf(row("1", "Alice"), row("2", "Bob")))

        val users = buildList {
            while (cursor.next().await()) {
                add(cursor.getLong(0)!! to cursor.getString(1)!!)
            }
        }

        assertEquals(listOf(1L to "Alice", 2L to "Bob"), users)
    }
}
