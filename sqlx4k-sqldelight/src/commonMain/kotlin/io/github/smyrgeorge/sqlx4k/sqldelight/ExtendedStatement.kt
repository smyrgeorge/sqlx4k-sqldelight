package io.github.smyrgeorge.sqlx4k.sqldelight

import io.github.smyrgeorge.sqlx4k.Dialect
import io.github.smyrgeorge.sqlx4k.SQLError
import io.github.smyrgeorge.sqlx4k.Statement
import io.github.smyrgeorge.sqlx4k.ValueEncoderRegistry
import io.github.smyrgeorge.sqlx4k.impl.statement.AbstractStatement
import io.github.smyrgeorge.sqlx4k.impl.types.TypedNull
import kotlin.reflect.KClass

/**
 * The `ExtendedStatement` class provides an implementation that extends the functionality
 * of an SQL statement, allowing the handling of PostgreSQL-style positional parameters.
 *
 * Positional parameters in the form `$1`, `$2`, etc., are supported and can be bound to
 * specific values. This is the placeholder syntax produced by SQLDelight's PostgreSQL
 * dialect, which is why this statement implementation lives in the SQLDelight integration
 * module.
 *
 * The statement never renders values into the SQL string. [renderNativeQuery] rewrites the
 * placeholders to the dialect-native syntax and collects the bound values in placeholder
 * order, so the driver can send them to the database server as true prepared-statement
 * parameters.
 *
 * When the SQL contains no `$n` placeholders (e.g. the `?` placeholders produced by
 * SQLDelight's MySQL dialect), all binding and rendering is delegated to [AbstractStatement],
 * which natively supports `?` and `:name` parameters.
 *
 * @param sql The SQL query string containing positional PostgreSQL-style parameters.
 */
class ExtendedStatement(sql: String) : AbstractStatement(sql) {

    /**
     * The number of positional PostgreSQL-style parameters extracted from the SQL statement,
     * i.e. the highest `$n` index found. Extraction skips comments, quoted and dollar-quoted
     * strings.
     */
    private val extractedDollarParameters: Int = extractDollarParameters()

    /**
     * An array storing the values bound to the PostgreSQL-style positional parameters.
     *
     * The array index corresponds to the zero-based parameter index (`$1` -> 0). The sentinel
     * [NOT_SET] distinguishes between "not yet bound" and "bound to null".
     */
    private val dollarParametersValues: Array<Any?> = Array(extractedDollarParameters) { NOT_SET }

    /**
     * Binds a value to a positional parameter in the prepared statement.
     *
     * @param index The zero-based positional index of the parameter to bind.
     * @param value The value to bind to the specified parameter.
     * @return The current `ExtendedStatement` instance with the bound parameter, allowing method chaining.
     * @throws SQLError if the provided index is out of bounds of the statement's parameters.
     */
    override fun bind(index: Int, value: Any?): ExtendedStatement {
        if (extractedDollarParameters == 0) {
            // No '$n' placeholders: delegate to the base '?' positional parameters.
            super.bind(index, value)
            return this
        }
        if (index !in 0..<extractedDollarParameters) {
            SQLError(
                code = SQLError.Code.PositionalParameterOutOfBounds,
                message = "Index '$index' out of bounds."
            ).raise()
        }
        dollarParametersValues[index] = value
        return this
    }

    /**
     * Binds a typed null to a positional parameter in the prepared statement.
     *
     * @param index The zero-based positional index of the parameter to bind.
     * @param type The Kotlin class corresponding to the intended SQL type of the parameter.
     * @return The current `ExtendedStatement` instance with the bound parameter, allowing method chaining.
     * @throws SQLError if the provided index is out of bounds of the statement's parameters.
     */
    override fun bindNull(index: Int, type: KClass<*>): ExtendedStatement {
        if (extractedDollarParameters == 0) {
            // No '$n' placeholders: delegate to the base '?' positional parameters.
            super.bindNull(index, type)
            return this
        }
        if (index !in 0..<extractedDollarParameters) {
            SQLError(
                code = SQLError.Code.PositionalParameterOutOfBounds,
                message = "Index '$index' out of bounds."
            ).raise()
        }
        dollarParametersValues[index] = TypedNull(type)
        return this
    }

    /**
     * Renders the SQL statement for native prepared-statement execution by replacing the
     * PostgreSQL-style `$n` placeholders with dialect-appropriate positional parameters and
     * collecting the bound values in placeholder order:
     *
     * - [Dialect.PostgreSQL]: `$1, $2, ...` (renumbered sequentially)
     * - [Dialect.MySQL], [Dialect.SQLite]: `?`
     *
     * A parameter referenced multiple times (e.g. `$1 ... $1`) has its value collected once per
     * occurrence. Placeholders inside comments, quoted and dollar-quoted strings are left as-is.
     *
     * When the SQL contains no `$n` placeholders, rendering is delegated to [AbstractStatement],
     * which handles `?` and `:name` parameters.
     *
     * @param dialect The `Dialect` instance representing the SQL dialect used for rendering the query.
     * @param encoders The `ValueEncoderRegistry` used to resolve and encode values for positional parameters.
     * @return A `Statement.NativeQuery` instance containing the rendered SQL query and its parameter values.
     * @throws SQLError if a value for a referenced positional parameter index is not supplied.
     */
    override fun renderNativeQuery(dialect: Dialect, encoders: ValueEncoderRegistry): Statement.NativeQuery {
        if (extractedDollarParameters == 0) return super.renderNativeQuery(dialect, encoders)

        val values = ArrayList<Any?>(extractedDollarParameters)
        var counter = 0
        val renderedSql = sql.renderWithScanner { i, c, sb ->
            if (c != '$') return@renderWithScanner null
            // attempt $<digits> (but skip dollar-quoted start, already handled by scanner)
            var j = i + 1
            if (j < length && this[j].isDigit()) {
                while (j < length && this[j].isDigit()) j++
                val idx1 = substring(i + 1, j).toIntOrNull() ?: return@renderWithScanner null
                if (idx1 < 1) return@renderWithScanner null
                values.add(resolveNative(getDollarValue(idx1 - 1), encoders))
                counter++
                when (dialect) {
                    Dialect.PostgreSQL -> sb.append('$').append(counter)
                    Dialect.MySQL, Dialect.SQLite -> sb.append('?')
                }
                return@renderWithScanner j
            }
            null
        }
        return Statement.NativeQuery(renderedSql, dialect, values)
    }

    /**
     * Retrieves the value bound to a PostgreSQL-style positional parameter at the specified index.
     *
     * @param index The zero-based index of the positional parameter whose bound value is to be retrieved.
     * @return The value bound to the specified positional parameter, or `null` if bound to null.
     * @throws SQLError if the value is not set for the specified index.
     */
    private fun getDollarValue(index: Int): Any? {
        val value = dollarParametersValues[index]
        if (value === NOT_SET) {
            SQLError(
                code = SQLError.Code.PositionalParameterValueNotSupplied,
                message = "Value for positional parameter index '$index' was not supplied."
            ).raise()
        }
        return value
    }

    private fun extractDollarParameters(): Int {
        var maxIndex = 0
        sql.scanWithExtractor { i, c ->
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
        return maxIndex
    }

    override fun toString(): String = "ExtendedStatement(sql='${sql.take(32)}...')"

    companion object {
        /**
         * Sentinel object used to distinguish between "not yet bound" and "bound to null"
         * in the positional parameters array.
         */
        private val NOT_SET = Any()
    }
}
