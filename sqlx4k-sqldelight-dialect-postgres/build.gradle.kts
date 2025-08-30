plugins {
    id("io.github.smyrgeorge.sqlx4k.multiplatform.jvm")
    id("io.github.smyrgeorge.sqlx4k.publish")
    id("io.github.smyrgeorge.sqlx4k.dokka")
}

kotlin {
    explicitApi()
    @Suppress("unused")
    sourceSets {
        configureEach {
            languageSettings.progressiveMode = true
        }
        val jvmMain by getting {
            dependencies {
                api(libs.sqldelight.postgresql.dialect)
                api(libs.sqldelight.dialect.api)
                compileOnly(libs.sqldelight.compiler.env)
            }
        }
    }
}
