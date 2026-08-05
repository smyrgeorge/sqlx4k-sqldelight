package io.github.smyrgeorge.sqlx4k.sqldelight.dialect.mysql

import app.cash.sqldelight.dialect.api.DialectType
import app.cash.sqldelight.dialect.api.IntermediateType
import app.cash.sqldelight.dialect.api.PrimitiveType
import app.cash.sqldelight.dialect.api.QueryWithResults
import app.cash.sqldelight.dialect.api.RuntimeTypes
import app.cash.sqldelight.dialect.api.SqlDelightDialect
import app.cash.sqldelight.dialect.api.TypeResolver
import app.cash.sqldelight.dialects.mysql.MySqlDialect
import app.cash.sqldelight.dialects.mysql.MySqlTypeResolver
import app.cash.sqldelight.dialects.mysql.grammar.MySqlParserUtil
import app.cash.sqldelight.dialects.mysql.grammar.psi.MySqlTypeName
import com.alecstrong.sql.psi.core.SqlParserUtil
import com.alecstrong.sql.psi.core.psi.SqlExpr
import com.alecstrong.sql.psi.core.psi.SqlFunctionExpr
import com.alecstrong.sql.psi.core.psi.SqlStmt
import com.alecstrong.sql.psi.core.psi.SqlTypeName
import com.intellij.psi.PsiElement
import com.squareup.kotlinpoet.BOOLEAN
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.DOUBLE
import com.squareup.kotlinpoet.INT
import com.squareup.kotlinpoet.LONG
import com.squareup.kotlinpoet.SHORT
import com.squareup.kotlinpoet.TypeName

/**
 * A custom dialect for MySQL using the Sqlx4k library with SqlDelight.
 * This class extends `SqlDelightDialect` and is implemented through the `MySqlDialect()`.
 * It provides specific configurations and type resolvers for MySQL.
 *
 * Original implementation found here:
 * https://github.com/joepeding/mysql-native-sqldelight/blob/main/mysql-native-dialect/src/main/kotlin/nl/joepeding/sqldelight/mysql/native/dialect/MysqlNativeDialect.kt
 */
public open class MySqlSqlx4kDialect : SqlDelightDialect by MySqlDialect() {

    override fun setup() {
        SqlParserUtil.reset()
        MySqlParserUtil.reset()
        MySqlParserUtil.overrideSqlParser()
    }

    override val runtimeTypes: RuntimeTypes
        get() = error("Only async driver is supported.")

    override val asyncRuntimeTypes: RuntimeTypes = RuntimeTypes(
        cursorType = ClassName(
            packageName = "io.github.smyrgeorge.sqlx4k.sqldelight",
            "SqlDelightCursor"
        ),
        preparedStatementType = ClassName(
            packageName = "io.github.smyrgeorge.sqlx4k.sqldelight",
            "SqlDelightPreparedStatement"
        )
    )

    override fun typeResolver(parentResolver: TypeResolver): TypeResolver =
        MySqlSqlx4kTypeResolver(parentResolver)

    private class MySqlSqlx4kTypeResolver(parentResolver: TypeResolver) : TypeResolver {
        private val parent = MySqlTypeResolver(parentResolver)

        override fun definitionType(typeName: SqlTypeName): IntermediateType = with(typeName) {
            check(this is MySqlTypeName)
            val type = IntermediateType(
                when {
                    smallIntDataType != null -> MySqlType.SMALL_INT
                    intDataType != null -> MySqlType.INTEGER
                    bigIntDataType != null -> MySqlType.BIG_INT
                    fixedPointDataType != null -> MySqlType.NUMERIC
                    approximateNumericDataType != null -> PrimitiveType.REAL
                    tinyIntDataType != null -> if (tinyIntDataType!!.text == "BOOLEAN") {
                        MySqlType.TINY_INT_BOOL
                    } else {
                        MySqlType.TINY_INT
                    }

                    mediumIntDataType != null -> MySqlType.INTEGER
                    dateDataType != null -> {
                        when (dateDataType!!.firstChild.text.uppercase()) {
                            "DATE" -> MySqlType.DATE
                            "TIME" -> MySqlType.TIME
                            "DATETIME" -> MySqlType.DATETIME
                            "TIMESTAMP" -> MySqlType.TIMESTAMP
                            "YEAR" -> PrimitiveType.TEXT
                            else -> throw IllegalArgumentException("Unknown date type ${dateDataType!!.text}")
                        }
                    }

                    bitDataType != null -> MySqlType.BIT
                    enumSetType != null -> PrimitiveType.TEXT
                    characterType != null -> PrimitiveType.TEXT
                    jsonDataType != null -> PrimitiveType.TEXT
                    binaryDataType != null -> PrimitiveType.BLOB
                    else -> throw IllegalArgumentException("Unknown kotlin type for sql type $text")
                }
            )
            return type
        }

        override fun resolvedType(expr: SqlExpr): IntermediateType =
            parent.resolvedType(expr).remapped()

        override fun argumentType(parent: PsiElement, argument: SqlExpr): IntermediateType =
            this.parent.argumentType(parent, argument).remapped()

        override fun functionType(functionExpr: SqlFunctionExpr): IntermediateType? =
            parent.functionType(functionExpr)?.remapped()

        override fun simplifyType(intermediateType: IntermediateType): IntermediateType =
            parent.simplifyType(intermediateType)

        override fun queryWithResults(sqlStmt: SqlStmt): QueryWithResults? =
            parent.queryWithResults(sqlStmt)

        /**
         * Remaps the JVM-only dialect types produced by the upstream MySQL resolver
         * (`java.time.*`, `java.math.BigDecimal`, ...) to their multiplatform sqlx4k
         * equivalents.
         *
         * Expressions that are not resolved through [definitionType] - such as function calls
         * (`MAX(...)`, `GREATEST(...)`, ...), `IF(...)` expressions and window functions - are
         * typed by the upstream resolver, so without this remapping the generated code would
         * reference JVM-only types and cursor/binder methods that do not exist in the sqlx4k
         * runtime.
         */
        private fun IntermediateType.remapped(): IntermediateType {
            // The upstream enum (app.cash.sqldelight.dialects.mysql.MySqlType) is internal,
            // so its entries are matched by name instead of by reference.
            val upstream = dialectType
            if (upstream !is Enum<*> ||
                upstream.javaClass.name != "app.cash.sqldelight.dialects.mysql.MySqlType"
            ) return this
            val remapped: DialectType = when (upstream.name) {
                "TINY_INT" -> MySqlType.TINY_INT
                "TINY_INT_BOOL" -> MySqlType.TINY_INT_BOOL
                "SMALL_INT" -> MySqlType.SMALL_INT
                "INTEGER" -> MySqlType.INTEGER
                "BIG_INT" -> MySqlType.BIG_INT
                "BIT" -> MySqlType.BIT
                "NUMERIC" -> MySqlType.NUMERIC
                "DATE" -> MySqlType.DATE
                "TIME" -> MySqlType.TIME
                "DATETIME" -> MySqlType.DATETIME
                "TIMESTAMP" -> MySqlType.TIMESTAMP
                else -> return this
            }
            return copy(
                dialectType = remapped,
                javaType = remapped.javaType.copy(nullable = javaType.isNullable)
            )
        }
    }

    private enum class MySqlType(override val javaType: TypeName) : DialectType {
        TINY_INT_BOOL(BOOLEAN) {
            override fun decode(value: CodeBlock) = CodeBlock.of("%L == 1L", value)
            override fun encode(value: CodeBlock) = CodeBlock.of("if (%L) 1L else 0L", value)
        },

        TINY_INT(SHORT),
        SMALL_INT(SHORT),
        INTEGER(INT),
        BIG_INT(LONG),
        BIT(BOOLEAN) {
            override fun decode(value: CodeBlock) = CodeBlock.of("%L == 1L", value)
            override fun encode(value: CodeBlock) = CodeBlock.of("if (%L) 1L else 0L", value)
        },

        /**
         * NUMERIC/DECIMAL values are mapped to [Double] since there is no multiplatform
         * arbitrary-precision decimal type. Values that exceed the precision of a [Double]
         * are not represented exactly.
         */
        NUMERIC(DOUBLE),
        DATE(ClassName("kotlinx.datetime", "LocalDate")),
        TIME(ClassName("kotlinx.datetime", "LocalTime")),
        TIMESTAMP(ClassName("kotlinx.datetime", "LocalDateTime")),

        /**
         * Both DATETIME and TIMESTAMP are mapped to `kotlinx.datetime.LocalDateTime`: MySQL
         * returns both as wall-clock date-time strings without a UTC offset, so an offset-aware
         * type cannot be decoded reliably.
         */
        DATETIME(ClassName("kotlinx.datetime", "LocalDateTime"));

        override fun prepareStatementBinder(columnIndex: CodeBlock, value: CodeBlock): CodeBlock {
            return CodeBlock.builder()
                .add(
                    when (this) {
                        TINY_INT -> "bindShort"
                        SMALL_INT -> "bindShort"
                        INTEGER -> "bindInt"
                        BIG_INT -> "bindLong"
                        NUMERIC -> "bindDouble"
                        DATE -> "bindDate"
                        TIME -> "bindTime"
                        DATETIME, TIMESTAMP -> "bindLocalTimestamp"
                        TINY_INT_BOOL -> "bindLong"
                        BIT -> "bindLong"
                    }
                )
                .add("(%L, %L)\n", columnIndex, value)
                .build()
        }

        override fun cursorGetter(columnIndex: Int, cursorName: String): CodeBlock {
            return CodeBlock.of(
                when (this) {
                    TINY_INT -> "$cursorName.getShort($columnIndex)"
                    SMALL_INT -> "$cursorName.getShort($columnIndex)"
                    INTEGER -> "$cursorName.getInt($columnIndex)"
                    BIG_INT -> "$cursorName.getLong($columnIndex)"
                    NUMERIC -> "$cursorName.getDouble($columnIndex)"
                    DATE -> "$cursorName.getDate($columnIndex)"
                    TIME -> "$cursorName.getTime($columnIndex)"
                    DATETIME, TIMESTAMP -> "$cursorName.getLocalTimestamp($columnIndex)"
                    TINY_INT_BOOL -> "$cursorName.getLong($columnIndex)"
                    BIT -> "$cursorName.getLong($columnIndex)"
                }
            )
        }
    }
}
