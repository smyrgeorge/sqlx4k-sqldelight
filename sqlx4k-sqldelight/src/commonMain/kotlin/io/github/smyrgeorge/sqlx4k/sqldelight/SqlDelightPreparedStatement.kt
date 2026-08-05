package io.github.smyrgeorge.sqlx4k.sqldelight

import app.cash.sqldelight.db.SqlPreparedStatement
import kotlinx.datetime.DateTimePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * A [SqlPreparedStatement] implementation that binds values to an [ExtendedStatement].
 *
 * Values are bound as-is (no local rendering): the underlying sqlx4k driver sends them to the
 * database server as native prepared-statement parameters. `null` values are bound as typed
 * nulls so the server receives the intended SQL type.
 */
class SqlDelightPreparedStatement(sql: String) : SqlPreparedStatement {
    val statement = ExtendedStatement(sql)

    override fun bindBoolean(index: Int, boolean: Boolean?) {
        if (boolean == null) statement.bindNull(index, Boolean::class)
        else statement.bind(index, boolean)
    }

    override fun bindBytes(index: Int, bytes: ByteArray?) {
        if (bytes == null) statement.bindNull(index, ByteArray::class)
        else statement.bind(index, bytes)
    }

    override fun bindDouble(index: Int, double: Double?) {
        if (double == null) statement.bindNull(index, Double::class)
        else statement.bind(index, double)
    }

    fun bindShort(index: Int, short: Short?) {
        if (short == null) statement.bindNull(index, Short::class)
        else statement.bind(index, short)
    }

    fun bindInt(index: Int, int: Int?) {
        if (int == null) statement.bindNull(index, Int::class)
        else statement.bind(index, int)
    }

    override fun bindLong(index: Int, long: Long?) {
        if (long == null) statement.bindNull(index, Long::class)
        else statement.bind(index, long)
    }

    override fun bindString(index: Int, string: String?) {
        if (string == null) statement.bindNull(index, String::class)
        else statement.bind(index, string)
    }

    fun bindDate(index: Int, value: LocalDate?) {
        if (value == null) statement.bindNull(index, LocalDate::class)
        else statement.bind(index, value)
    }

    fun bindTime(index: Int, value: LocalTime?) {
        if (value == null) statement.bindNull(index, LocalTime::class)
        else statement.bind(index, value)
    }

    fun bindLocalTimestamp(index: Int, value: LocalDateTime?) {
        if (value == null) statement.bindNull(index, LocalDateTime::class)
        else statement.bind(index, value)
    }

    fun bindTimestamp(index: Int, value: Instant?) {
        if (value == null) statement.bindNull(index, Instant::class)
        else statement.bind(index, value)
    }

    fun bindInterval(index: Int, value: DateTimePeriod?) {
        // There is no native interval parameter type; bind the ISO-8601 representation as text
        // and let the server cast it to the interval type.
        if (value == null) statement.bindNull(index, String::class)
        else statement.bind(index, value.toString())
    }

    fun bindUuid(index: Int, value: Uuid?) {
        if (value == null) statement.bindNull(index, Uuid::class)
        else statement.bind(index, value)
    }
}
