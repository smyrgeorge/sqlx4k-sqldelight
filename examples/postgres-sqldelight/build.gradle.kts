plugins {
    id("io.github.smyrgeorge.sqlx4k.multiplatform.examples")
    alias(libs.plugins.sqldeligh)
}

kotlin {
    sourceSets {
        @Suppress("unused")
        val commonMain by getting {
            dependencies {
                implementation(libs.sqlx4k.postgres)
                implementation(project(":sqlx4k-sqldelight"))
            }
        }
    }
}

sqldelight {
    linkSqlite = false
    databases.register("Database") {
        generateAsync = true
        packageName = "db.entities"
        dialect(project(":sqlx4k-sqldelight-dialect-postgres"))
    }
}
