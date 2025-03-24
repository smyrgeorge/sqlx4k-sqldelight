plugins {
    id("io.github.smyrgeorge.sqlx4k.multiplatform.jvm")
    id("io.github.smyrgeorge.sqlx4k.publish")
}

kotlin {
    explicitApi()
    sourceSets {
        configureEach {
            languageSettings.progressiveMode = true
        }
        @Suppress("unused")
        val jvmMain by getting {
            dependencies {
                api(libs.sqldelight.postgresql.dialect)
                api(libs.sqldelight.dialect.api)
                compileOnly(libs.sqldelight.compiler.env)
            }
        }
    }
}
