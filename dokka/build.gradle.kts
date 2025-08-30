plugins {
    id("io.github.smyrgeorge.sqlx4k.dokka")
}

dependencies {
    dokka(project(":sqlx4k-sqldelight"))
    dokka(project(":sqlx4k-sqldelight-dialect-mysql"))
    dokka(project(":sqlx4k-sqldelight-dialect-postgres"))
}

dokka {
    moduleName.set(rootProject.name)
}
