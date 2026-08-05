rootProject.name = "sqlx4k-sqldelight"

pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }

    includeBuild("build-logic")
}

include("sqlx4k-sqldelight")
include("sqlx4k-sqldelight-dialect-mysql")
include("sqlx4k-sqldelight-dialect-postgres")
include("sqlx4k-sqldelight-dialect-sqlite")

include("dokka")
include("examples:mysql-sqldelight")
include("examples:postgres-sqldelight")
include("examples:sqlite-sqldelight")
