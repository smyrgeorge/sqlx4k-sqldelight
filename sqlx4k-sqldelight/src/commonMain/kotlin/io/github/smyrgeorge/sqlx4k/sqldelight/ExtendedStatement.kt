@file:OptIn(ExperimentalUuidApi::class)

package io.github.smyrgeorge.sqlx4k.sqldelight

import io.github.smyrgeorge.sqlx4k.Dialect
import io.github.smyrgeorge.sqlx4k.SQLError
import io.github.smyrgeorge.sqlx4k.Statement
import io.github.smyrgeorge.sqlx4k.ValueEncoderRegistry
import io.github.smyrgeorge.sqlx4k.impl.extensions.toTimestampString
import io.github.smyrgeorge.sqlx4k.impl.statement.AbstractStatement
import io.github.smyrgeorge.sqlx4k.impl.types.NoWrappingTuple
import io.github.smyrgeorge.sqlx4k.impl.types.SqlRawLiteral
import io.github.smyrgeorge.sqlx4k.impl.types.TypedNull
import kotlin.reflect.KClass
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime

/**
 * The `ExtendedStatement` class provides an implementation that extends the functionality
 * of an SQL statement, allowing the handling of PostgreSQL-style positional parameters.
 *
 * Positional parameters in the form `$1`, `$2`, etc., are supported and can be bound to
 * specific values, encoded, and rendered into a final SQL statement.
 *
 * This is the placeholder syntax produced by SQLDelight's PostgreSQL dialect, which is why
 * this statement implementation lives in the SQLDelight integration module.
 *
 * @param sql The SQL query string containing positional PostgreSQL-style parameters.
 */
class ExtendedStatement(sql: String) : AbstractStatement(sql) {

    /**
     * A list of positional PostgreSQL-style parameter indices extracted from the SQL statement.
     * The list is [0, 1, 2, ...] sized to the number of occurrences of $n placeholders.
     * Extraction skips comments, quoted and dollar-quoted strings.
     */
    private val pgParameters: List<Int> = extractPgParameters()

    /**
     * A mutable map used for storing the values of positional parameters within
     * the prepared SQL statement of the `ExtendedStatement` class.
     *
     * The keys in the map represent the zero-based positional indices of the
     * parameters in the SQL statement, while the corresponding values represent
     * the parameter values. These values can be `null` for nullable parameters.
     *
     * This map is primarily used to manage, bind, and retrieve parameter values
     * during statement preparation and rendering processes.
     */
    private val pgParametersValues: MutableMap<Int, Any?> = mutableMapOf()

    /**
     * Binds a value to a positional parameter in the prepared statement.
     *
     * @param index The zero-based positional index of the parameter to bind.
     * @param value The value to bind to the specified parameter.
     * @return The current `ExtendedStatement` instance with the bound parameter, allowing method chaining.
     * @throws SQLError if the provided index is out of bounds of the statement's parameters.
     */
    override fun bind(index: Int, value: Any?): ExtendedStatement {
        if (index < 0 || index >= pgParameters.size) {
            SQLError(
                code = SQLError.Code.PositionalParameterOutOfBounds,
                message = "Index '$index' out of bounds."
            ).raise()
        }
        pgParametersValues[index] = value
        return this
    }

    override fun bindNull(index: Int, type: KClass<*>): ExtendedStatement {
        if (index < 0 || index >= pgParameters.size) {
            SQLError(
                code = SQLError.Code.PositionalParameterOutOfBounds,
                message = "Index '$index' out of bounds."
            ).raise()
        }
        pgParametersValues[index] = TypedNull(type)
        return this
    }

    /**
     * Renders a native SQL query by resolving positional parameters and encoding their values
     * using the specified encoder registry for the given SQL dialect.
     *
     * @param dialect The `Dialect` instance representing the SQL dialect used for rendering the query.
     * @param encoders The `ValueEncoderRegistry` used to resolve and encode values for positional parameters.
     * @return A `Statement.NativeQuery` instance containing the rendered SQL query and its encoded parameter values.
     * @throws SQLError if a value for a positional parameter index is not supplied.
     */
    override fun renderNativeQuery(dialect: Dialect, encoders: ValueEncoderRegistry): Statement.NativeQuery {
        if (pgParameters.isEmpty()) return Statement.NativeQuery(sql, dialect, emptyList())
        val values = List(pgParameters.size) { index ->
            if (index !in pgParametersValues) {
                SQLError(
                    code = SQLError.Code.PositionalParameterValueNotSupplied,
                    message = "Value for positional parameter index '$index' was not supplied."
                ).raise()
            }
            resolveNative(pgParametersValues[index], encoders)
        }
        return Statement.NativeQuery(sql, dialect, values)
    }

    /**
     * Renders the SQL statement, including encoding all positional parameters using the specified encoder registry.
     *
     * @param encoders The `ValueEncoderRegistry` that provides the appropriate encoders for the parameter values.
     * @return A string representing the fully rendered SQL statement with all parameters encoded.
     */
    fun render(encoders: ValueEncoderRegistry = ValueEncoderRegistry.EMPTY): String =
        sql.renderPgParameters(encoders)

    /**
     * Replaces positional parameters in the SQL statement with their corresponding encoded values.
     *
     * @param encoders The `ValueEncoderRegistry` that provides the appropriate encoders for the parameter values.
     * @return The SQL statement with all positional parameters replaced by their encoded values.
     * @throws SQLError if a value for a positional parameter index is not supplied.
     */
    private fun String.renderPgParameters(encoders: ValueEncoderRegistry): String =
        renderWithScanner { i, c, sb ->
            if (c != '$') return@renderWithScanner null
            // attempt $<digits> (but skip dollar-quoted start, already handled by scanner)
            var j = i + 1
            if (j < length && this[j].isDigit()) {
                while (j < length && this[j].isDigit()) j++
                val numStr = substring(i + 1, j)
                val idx1 = numStr.toIntOrNull() ?: return@renderWithScanner null
                val zeroIdx = idx1 - 1
                if (zeroIdx !in pgParametersValues) {
                    SQLError(
                        code = SQLError.Code.PositionalParameterValueNotSupplied,
                        message = "Value for positional parameter index '$zeroIdx' was not supplied."
                    ).raise()
                }
                sb.append(encode(pgParametersValues[zeroIdx], encoders))
                return@renderWithScanner j
            }
            null
        }

    /**
     * Encodes a value into its SQL literal representation.
     *
     * Handles `null`, strings (with single-quote escaping), characters, numbers, booleans,
     * temporal types, UUIDs, enums, raw SQL literals, and collections (rendered as tuples).
     * For any other type, an encoder is looked up in the provided registry.
     *
     * @param value The value to encode.
     * @param encoders The `ValueEncoderRegistry` used to encode custom types.
     * @return The SQL literal representation of the value.
     * @throws SQLError if the type of the value is unsupported and no appropriate encoder is found.
     */
    private fun encode(value: Any?, encoders: ValueEncoderRegistry): String {
        return when (value) {
            null -> "null"
            is TypedNull -> "null"
            is String -> {
                // Fast path: if no single quote present, avoid replace allocation
                if (value.indexOf('\'') < 0) "'${value}'"
                // https://stackoverflow.com/questions/12316953/insert-text-with-single-quotes-in-postgresql
                // https://stackoverflow.com/questions/9596652/how-to-escape-apostrophe-a-single-quote-in-mysql
                // https://stackoverflow.com/questions/603572/escape-single-quote-character-for-use-in-an-sqlite-query
                else "'${value.replace("'", "''")}'"
            }

            is Char -> "'${if (value == '\'') "''" else value}'"
            is Boolean, is Byte, is Short, is Int, is Long, is Float, is Double -> value.toString()
            is Instant -> "'${value.toTimestampString()}'"
            is LocalDate, is LocalTime, is LocalDateTime -> "'${value}'"
            is Uuid -> "'${value}'"
            is Enum<*> -> "'${value.name}'"
            is SqlRawLiteral -> value.sql
            is Iterable<*> -> encodeTuple(value, encoders)
            is BooleanArray -> encodeTuple(value.asIterable(), encoders)
            is ShortArray -> encodeTuple(value.asIterable(), encoders)
            is IntArray -> encodeTuple(value.asIterable(), encoders)
            is LongArray -> encodeTuple(value.asIterable(), encoders)
            is FloatArray -> encodeTuple(value.asIterable(), encoders)
            is DoubleArray -> encodeTuple(value.asIterable(), encoders)
            is Array<*> -> encodeTuple(value.asIterable(), encoders)
            is NoWrappingTuple -> encodeTuple(value.value, encoders, wrapInParenthesis = false)

            else -> {
                val encoder = encoders.get(value::class)
                    ?: SQLError(
                        code = SQLError.Code.MissingValueConverter,
                        message = "Could not encode value of type ${value::class.simpleName}"
                    ).raise()
                encode(encoder.encode(value), encoders)
            }
        }
    }

    /**
     * Encodes a collection of values as a SQL tuple like `(a, b, c)`.
     * Uses [encode] for each element; nulls become `null` without quotes.
     */
    private fun encodeTuple(
        values: Iterable<*>,
        encoders: ValueEncoderRegistry,
        wrapInParenthesis: Boolean = true,
    ): String {
        if (!values.iterator().hasNext()) SQLError(
            code = SQLError.Code.EmptyCollection,
            message = "Cannot expand an empty collection as a SQL parameter"
        ).raise()
        return if (wrapInParenthesis) values.joinToString(", ", "(", ")") { encode(it, encoders) }
        else values.joinToString(", ") { encode(it, encoders) }
    }

    private fun extractPgParameters(): List<Int> {
        var maxIndex = 0
        val s = sql
        s.scanWithExtractor { i, c ->
            if (c != '$') return@scanWithExtractor null
            var j = i + 1
            if (j < length && this[j].isDigit()) {
                while (j < length && this[j].isDigit()) j++
                val idx1 = substring(i + 1, j).toIntOrNull() ?: 0
                if (idx1 > maxIndex) maxIndex = idx1
                return@scanWithExtractor j
            }
            null
        }
        return List(maxIndex) { it }
    }

    override fun toString(): String = "ExtendedStatement(sql='${sql.take(32)}...')"
}
