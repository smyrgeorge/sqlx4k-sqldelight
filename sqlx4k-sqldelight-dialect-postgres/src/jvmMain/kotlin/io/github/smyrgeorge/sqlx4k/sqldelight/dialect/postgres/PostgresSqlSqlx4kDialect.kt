package io.github.smyrgeorge.sqlx4k.sqldelight.dialect.postgres

import app.cash.sqldelight.dialect.api.DialectType
import app.cash.sqldelight.dialect.api.IntermediateType
import app.cash.sqldelight.dialect.api.PrimitiveType
import app.cash.sqldelight.dialect.api.QueryWithResults
import app.cash.sqldelight.dialect.api.RuntimeTypes
import app.cash.sqldelight.dialect.api.SqlDelightDialect
import app.cash.sqldelight.dialect.api.TypeResolver
import app.cash.sqldelight.dialects.postgresql.PostgreSqlDialect
import app.cash.sqldelight.dialects.postgresql.PostgreSqlTypeResolver
import app.cash.sqldelight.dialects.postgresql.grammar.PostgreSqlParserUtil
import app.cash.sqldelight.dialects.postgresql.grammar.psi.PostgreSqlTypeName
import com.alecstrong.sql.psi.core.SqlParserUtil
import com.alecstrong.sql.psi.core.psi.SqlExpr
import com.alecstrong.sql.psi.core.psi.SqlFunctionExpr
import com.alecstrong.sql.psi.core.psi.SqlStmt
import com.alecstrong.sql.psi.core.psi.SqlTypeName
import com.intellij.psi.PsiElement
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.DOUBLE
import com.squareup.kotlinpoet.INT
import com.squareup.kotlinpoet.LONG
import com.squareup.kotlinpoet.SHORT
import com.squareup.kotlinpoet.TypeName
import app.cash.sqldelight.dialects.postgresql.PostgreSqlType as UpstreamPostgreSqlType

/**
 * A custom dialect for PostgreSQL using the Sqlx4k library with SqlDelight.
 * This class extends `SqlDelightDialect` and is implemented through the `PostgreSqlDialect()`.
 * It provides specific configurations and type resolvers for PostgreSQL.
 *
 * Original implementation found here:
 * https://github.com/hfhbd/postgres-native-sqldelight/blob/b7ec77f5dbd1943b16087e830529e5e6f1861017/postgres-native-sqldelight-dialect/src/main/kotlin/app/softwork/sqldelight/postgresdialect/PostgresNativeDialect.kt
 */
public open class PostgresSqlSqlx4kDialect : SqlDelightDialect by PostgreSqlDialect() {

    override fun setup() {
        SqlParserUtil.reset()
        PostgreSqlParserUtil.reset()
        PostgreSqlParserUtil.overrideSqlParser()
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
        PostgreSqlSqlx4kTypeResolver(parentResolver)

    internal class PostgreSqlSqlx4kTypeResolver(parentResolver: TypeResolver) : TypeResolver {
        private val parent = PostgreSqlTypeResolver(parentResolver)

        override fun definitionType(typeName: SqlTypeName): IntermediateType = with(typeName) {
            check(this is PostgreSqlTypeName)
            if (node.getChildren(null).map { it.text }.takeLast(2) == listOf("[", "]")) {
                throw IllegalArgumentException("Array types are not supported by the sqlx4k dialect: $text")
            }
            val type = IntermediateType(
                when {
                    smallIntDataType != null -> PostgreSqlType.SMALL_INT
                    intDataType != null -> PostgreSqlType.INTEGER
                    bigIntDataType != null -> PostgreSqlType.BIG_INT
                    numericDataType != null -> PostgreSqlType.NUMERIC
                    approximateNumericDataType != null -> PrimitiveType.REAL
                    stringDataType != null -> PrimitiveType.TEXT
                    uuidDataType != null -> PostgreSqlType.UUID
                    smallSerialDataType != null -> PostgreSqlType.SMALL_INT
                    serialDataType != null -> PostgreSqlType.INTEGER
                    bigSerialDataType != null -> PostgreSqlType.BIG_INT
                    dateDataType != null -> {
                        when (dateDataType!!.firstChild.text) {
                            "DATE" -> PostgreSqlType.DATE
                            "TIME" -> PostgreSqlType.TIME
                            "TIMESTAMP" -> if (dateDataType!!.node.getChildren(null)
                                    .any { it.text == "WITH" }
                            ) PostgreSqlType.TIMESTAMP_TIMEZONE else PostgreSqlType.TIMESTAMP

                            "TIMESTAMPTZ" -> PostgreSqlType.TIMESTAMP_TIMEZONE
                            "INTERVAL" -> PostgreSqlType.INTERVAL
                            else -> throw IllegalArgumentException("Unknown date type ${dateDataType!!.text}")
                        }
                    }

                    jsonDataType != null -> PrimitiveType.TEXT
                    booleanDataType != null -> PrimitiveType.BOOLEAN
                    blobDataType != null -> PrimitiveType.BLOB
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
         * Remaps the JVM-only dialect types produced by the upstream PostgreSQL resolver
         * (`java.time.*`, `java.util.UUID`, `java.math.BigDecimal`, ...) to their multiplatform
         * sqlx4k equivalents.
         *
         * Expressions that are not resolved through [definitionType] - such as function calls
         * (`MAX(...)`, `NOW()`, ...) and casts - are typed by the upstream resolver, so without
         * this remapping the generated code would reference JVM-only types and cursor/binder
         * methods that do not exist in the sqlx4k runtime.
         */
        internal fun IntermediateType.remapped(): IntermediateType {
            val remapped: DialectType = when (dialectType) {
                UpstreamPostgreSqlType.SMALL_INT -> PostgreSqlType.SMALL_INT
                UpstreamPostgreSqlType.INTEGER -> PostgreSqlType.INTEGER
                UpstreamPostgreSqlType.BIG_INT -> PostgreSqlType.BIG_INT
                UpstreamPostgreSqlType.NUMERIC -> PostgreSqlType.NUMERIC
                UpstreamPostgreSqlType.DATE -> PostgreSqlType.DATE
                UpstreamPostgreSqlType.TIME -> PostgreSqlType.TIME
                UpstreamPostgreSqlType.TIMESTAMP -> PostgreSqlType.TIMESTAMP
                UpstreamPostgreSqlType.TIMESTAMP_TIMEZONE -> PostgreSqlType.TIMESTAMP_TIMEZONE
                UpstreamPostgreSqlType.INTERVAL -> PostgreSqlType.INTERVAL
                UpstreamPostgreSqlType.UUID -> PostgreSqlType.UUID
                UpstreamPostgreSqlType.JSON,
                UpstreamPostgreSqlType.TSVECTOR,
                UpstreamPostgreSqlType.TSQUERY,
                UpstreamPostgreSqlType.TSRANGE,
                UpstreamPostgreSqlType.TSTZRANGE,
                UpstreamPostgreSqlType.TSMULTIRANGE,
                UpstreamPostgreSqlType.TSTZMULTIRANGE,
                UpstreamPostgreSqlType.XML,
                UpstreamPostgreSqlType.ENUM,
                UpstreamPostgreSqlType.GEOMETRY,
                UpstreamPostgreSqlType.GEOGRAPHY -> PrimitiveType.TEXT

                else -> return this
            }
            return copy(
                dialectType = remapped,
                javaType = remapped.javaType.copy(nullable = javaType.isNullable)
            )
        }
    }

    internal enum class PostgreSqlType(override val javaType: TypeName) : DialectType {
        SMALL_INT(SHORT),
        INTEGER(INT),
        BIG_INT(LONG),

        /**
         * NUMERIC/DECIMAL values are mapped to [Double] since there is no multiplatform
         * arbitrary-precision decimal type. Values that exceed the precision of a [Double]
         * are not represented exactly.
         */
        NUMERIC(DOUBLE),
        DATE(ClassName("kotlinx.datetime", "LocalDate")),
        TIME(ClassName("kotlinx.datetime", "LocalTime")),
        TIMESTAMP(ClassName("kotlinx.datetime", "LocalDateTime")),
        INTERVAL(ClassName("kotlinx.datetime", "DateTimePeriod")),
        TIMESTAMP_TIMEZONE(ClassName("kotlin.time", "Instant")),
        UUID(ClassName("kotlin.uuid", "Uuid"));

        override fun prepareStatementBinder(columnIndex: CodeBlock, value: CodeBlock): CodeBlock {
            return CodeBlock.builder()
                .add(
                    when (this) {
                        SMALL_INT -> "bindShort"
                        INTEGER -> "bindInt"
                        BIG_INT -> "bindLong"
                        NUMERIC -> "bindDouble"
                        DATE -> "bindDate"
                        TIME -> "bindTime"
                        TIMESTAMP -> "bindLocalTimestamp"
                        TIMESTAMP_TIMEZONE -> "bindTimestamp"
                        INTERVAL -> "bindInterval"
                        UUID -> "bindUuid"
                    }
                )
                .add("(%L, %L)\n", columnIndex, value)
                .build()
        }

        override fun cursorGetter(columnIndex: Int, cursorName: String): CodeBlock {
            return CodeBlock.of(
                when (this) {
                    SMALL_INT -> "$cursorName.getShort($columnIndex)"
                    INTEGER -> "$cursorName.getInt($columnIndex)"
                    BIG_INT -> "$cursorName.getLong($columnIndex)"
                    NUMERIC -> "$cursorName.getDouble($columnIndex)"
                    DATE -> "$cursorName.getDate($columnIndex)"
                    TIME -> "$cursorName.getTime($columnIndex)"
                    TIMESTAMP -> "$cursorName.getLocalTimestamp($columnIndex)"
                    TIMESTAMP_TIMEZONE -> "$cursorName.getTimestamp($columnIndex)"
                    INTERVAL -> "$cursorName.getInterval($columnIndex)"
                    UUID -> "$cursorName.getUuid($columnIndex)"
                }
            )
        }
    }
}
