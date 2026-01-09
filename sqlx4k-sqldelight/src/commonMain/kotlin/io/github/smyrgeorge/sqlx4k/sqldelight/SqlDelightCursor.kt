@file:OptIn(ExperimentalTime::class)

package io.github.smyrgeorge.sqlx4k.sqldelight

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlCursor
import io.github.smyrgeorge.sqlx4k.ResultSet
import kotlinx.datetime.DateTimePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
@Suppress("unused", "RedundantNullableReturnType")
class SqlDelightCursor(
    result: ResultSet
) : SqlCursor {
    private var iterator: Iterator<ResultSet.Row> = result.iterator()
    private lateinit var current: ResultSet.Row

    override fun next(): QueryResult.AsyncValue<Boolean> = QueryResult.AsyncValue {
        if (!iterator.hasNext()) return@AsyncValue false
        current = iterator.next()
        true
    }

    override fun getBoolean(index: Int): Boolean? {
        return getString(index)?.lowercase()?.let { value ->
            when (value) {
                "true", "t", "1" -> true
                "false", "f", "0" -> false
                else -> null
            }
        }
    }

    override fun getBytes(index: Int): ByteArray? = error("This feature is not yes supported.")
    override fun getDouble(index: Int): Double? = getString(index)?.toDouble()
    fun getShort(index: Int): Short? = getString(index)?.toShort()
    fun getInt(index: Int): Int? = getString(index)?.toInt()
    override fun getLong(index: Int): Long? = getString(index)?.toLong()
    override fun getString(index: Int): String? = current.get(index).asStringOrNull()
    fun getDate(index: Int): LocalDate? = getString(index)?.let { LocalDate.parse(it) }
    fun getTime(index: Int): LocalTime? = getString(index)?.let { LocalTime.parse(it) }
    fun getLocalTimestamp(index: Int): LocalDateTime? = getString(index)?.replace(" ", "T")
        ?.let { LocalDateTime.parse(it) }

    fun getTimestamp(index: Int): Instant? = getString(index)?.let {
        Instant.parse(it.replace(" ", "T"))
    }

    fun getInterval(index: Int): DateTimePeriod? = getString(index)?.let { DateTimePeriod.parse(it) }
    fun getUuid(index: Int): Uuid? = getString(index)?.let { Uuid.parse(it) }
}
