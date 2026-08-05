import app.cash.sqldelight.async.coroutines.awaitAsList
import db.entities.Customer
import db.entities.Database
import io.github.smyrgeorge.sqlx4k.ConnectionPool
import io.github.smyrgeorge.sqlx4k.postgres.postgreSQL
import io.github.smyrgeorge.sqlx4k.sqldelight.Sqlx4kSqldelightDriver
import kotlinx.coroutines.runBlocking

fun main() {
    runBlocking {
        val options = ConnectionPool.Options.builder()
            .maxConnections(20)
            .build()

        val postgres = postgreSQL(
            url = "postgresql://localhost:15432/test",
            username = "postgres",
            password = "postgres",
            options = options
        )

        val sqldelightDriver = Sqlx4kSqldelightDriver(postgres)
        val db = Database(sqldelightDriver)

        val sql = """
            CREATE TABLE IF NOT EXISTS customer (
              id SERIAL PRIMARY KEY NOT NULL,
              name VARCHAR(255) NOT NULL
            );
        """.trimIndent()
        postgres.execute(sql).getOrThrow()

        postgres.execute("delete from customer;").getOrThrow()
        db.customerQueries.insert(1, "John 1")
        db.customerQueries.insert(2, "John 2")
        val customers: List<Customer> = db.customerQueries.getAllCustomers().awaitAsList()
        println(customers)
    }
}
