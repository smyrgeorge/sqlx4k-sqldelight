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

// Configure test tasks to pass Docker environment to TestContainers
tasks.withType<Test> {
    environment("DOCKER_HOST", System.getenv("DOCKER_HOST") ?: "unix:///var/run/docker.sock")
    environment("TESTCONTAINERS_RYUK_DISABLED", "true")
    // Set Docker API version to 1.44 (minimum level for modern Docker versions)
    environment("DOCKER_API_VERSION", "1.44")
    systemProperty("api.version", "1.44")
}
