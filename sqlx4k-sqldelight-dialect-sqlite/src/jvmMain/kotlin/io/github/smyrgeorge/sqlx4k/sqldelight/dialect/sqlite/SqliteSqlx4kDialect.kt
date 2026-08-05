package io.github.smyrgeorge.sqlx4k.sqldelight.dialect.sqlite

import app.cash.sqldelight.dialect.api.RuntimeTypes
import app.cash.sqldelight.dialect.api.SqlDelightDialect
import app.cash.sqldelight.dialects.sqlite_3_38.SqliteDialect
import com.squareup.kotlinpoet.ClassName

/**
 * A custom dialect for SQLite using the Sqlx4k library with SqlDelight.
 * This class extends `SqlDelightDialect` and is implemented through the `SqliteDialect()`
 * (SQLite 3.38). It provides specific configurations for the sqlx4k runtime.
 *
 * Unlike the PostgreSQL and MySQL dialects, no custom type resolver is required: the SQLite
 * type resolvers only produce SqlDelight's primitive types (`Long`, `Double`, `String`,
 * `ByteArray`, `Boolean`), which are multiplatform-safe and map directly to the binder and
 * cursor methods of the sqlx4k runtime.
 */
public open class SqliteSqlx4kDialect : SqlDelightDialect by SqliteDialect() {

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
}
