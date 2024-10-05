plugins {
    id("io.github.smyrgeorge.sqlx4k.publish")
    id("io.github.smyrgeorge.sqlx4k.multiplatform.simple")
}

kotlin {
    sourceSets {
        configureEach {
            languageSettings.progressiveMode = true
        }
        val nativeMain by getting {
            dependencies {
                api(libs.sqlx4k)
                api(libs.sqldeligh)
                api(libs.kotlinx.datetime)
                api(libs.stately.concurrency)
            }
        }
    }
}
