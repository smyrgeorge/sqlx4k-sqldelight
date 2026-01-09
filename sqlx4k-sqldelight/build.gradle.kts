plugins {
    id("io.github.smyrgeorge.sqlx4k.multiplatform.simple")
    id("io.github.smyrgeorge.sqlx4k.publish")
    id("io.github.smyrgeorge.sqlx4k.dokka")
}

kotlin {
    @Suppress("unused")
    sourceSets {
        configureEach {
            languageSettings.progressiveMode = true
        }
        val commonMain by getting {
            dependencies {
                api(libs.sqlx4k)
                api(libs.sqldeligh)
                api(libs.kotlinx.datetime)
                api(libs.stately.concurrency)
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation(libs.kotlinx.coroutines.test)
            }
        }
        val jvmTest by getting {
            dependencies {
                implementation(libs.sqlx4k.postgres)
                implementation(libs.sqlx4k.mysql)
                implementation(libs.testcontainers)
                implementation(libs.testcontainers.postgresql)
                implementation(libs.testcontainers.mysql)
            }
        }
    }
}
