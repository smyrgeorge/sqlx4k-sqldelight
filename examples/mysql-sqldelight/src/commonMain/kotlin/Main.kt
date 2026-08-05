import app.cash.sqldelight.async.coroutines.awaitAsList
import db.entities.Customer
import db.entities.Database
import io.github.smyrgeorge.sqlx4k.ConnectionPool
import io.github.smyrgeorge.sqlx4k.mysql.mySQL
import io.github.smyrgeorge.sqlx4k.sqldelight.Sqlx4kSqldelightDriver
import kotlinx.coroutines.runBlocking

fun main() {
    runBlocking {
        val options = ConnectionPool.Options.builder()
            .maxConnections(20)
            .build()

        val mysql = mySQL(
            url = "mysql://localhost:13306/test",
            username = "mysql",
            password = "mysql",
            options = options
        )

        val sqldelightDriver = Sqlx4kSqldelightDriver(mysql)
        val db = Database(sqldelightDriver)

        val sql = """
            CREATE TABLE IF NOT EXISTS customer (
              id INTEGER PRIMARY KEY NOT NULL,
              name VARCHAR(255) NOT NULL
            );
        """.trimIndent()
        mysql.execute(sql).getOrThrow()

        mysql.execute("delete from customer;").getOrThrow()
        db.customerQueries.insert(1, "John 1")
        db.customerQueries.insert(2, "John 2")
        val customers: List<Customer> = db.customerQueries.getAllCustomers().awaitAsList()
        println(customers)
    }
}
