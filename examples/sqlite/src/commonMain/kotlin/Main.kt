import app.cash.sqldelight.async.coroutines.awaitAsList
import db.entities.Customer
import db.entities.Database
import io.github.smyrgeorge.sqlx4k.ConnectionPool
import io.github.smyrgeorge.sqlx4k.sqlite.sqlite
import io.github.smyrgeorge.sqlx4k.sqldelight.Sqlx4kSqldelightDriver
import kotlinx.coroutines.runBlocking

fun main() {
    runBlocking {
        val options = ConnectionPool.Options.builder()
            .maxConnections(10)
            .build()

        val db = sqlite(
            url = "sqlite://test.db",
            options = options
        )

        val sqldelightDriver = Sqlx4kSqldelightDriver(db)
        val database = Database(sqldelightDriver)

        val sql = """
            CREATE TABLE IF NOT EXISTS customer (
              id INTEGER PRIMARY KEY NOT NULL,
              name TEXT NOT NULL
            );
        """.trimIndent()
        db.execute(sql).getOrThrow()

        db.execute("delete from customer;").getOrThrow()
        database.customerQueries.insert(1, "John 1")
        database.customerQueries.insert(2, "John 2")
        val customers: List<Customer> = database.customerQueries.getAllCustomers().awaitAsList()
        println(customers)
    }
}
