# Sqlx4k-sqldelight

![Build](https://github.com/smyrgeorge/sqlx4k-sqldelight/actions/workflows/ci.yml/badge.svg)
![Maven Central](https://img.shields.io/maven-central/v/io.github.smyrgeorge/sqlx4k-sqldelight)
![GitHub License](https://img.shields.io/github/license/smyrgeorge/sqlx4k-sqldelight)
![GitHub commit activity](https://img.shields.io/github/commit-activity/w/smyrgeorge/sqlx4k-sqldelight)
![GitHub issues](https://img.shields.io/github/issues/smyrgeorge/sqlx4k-sqldelight)
[![Kotlin](https://img.shields.io/badge/kotlin-2.1.20-blue.svg?logo=kotlin)](http://kotlinlang.org)

A high-performance, non-blocking database driver for PostgreSQL, MySQL, and SQLite, written for Kotlin Native.
Looking to build efficient, cross-platform applications with Kotlin Native.

This repository only contains the necessary parts for the `sqldelight` integration.
If you are looking the driver implementation, you can find it here: https://github.com/smyrgeorge/sqlx4k

📖 [Documentation](https://smyrgeorge.github.io/sqlx4k-sqldelight/)

🏠 [Homepage](https://smyrgeorge.github.io/) (under construction)

## Usage

Only `PostgreSQL` and `MySQL` is supported for now.

```kotlin
// build.gradle.kts

kotlin {
    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation("io.github.smyrgeorge:sqlx4k-postgres:x.y.z")
                implementation("io.github.smyrgeorge:sqlx4k-sqldelight:x.y.z")
            }
        }
    }
}

sqldelight {
    databases.register("Database") {
        generateAsync = true
        packageName = "db.entities"
        dialect("io.github.smyrgeorge:sqlx4k-sqldelight-dialect-postgres:x.y.z")
        // Or 'io.github.smyrgeorge:sqlx4k-sqldelight-dialect-mysql:x.y.z' for MySQl. 
    }
}
```

Check the examples for more information.

## Supported targets

We support the following targets:

- iosArm64
- androidNativeX64
- androidNativeArm64
- macosArm64
- macosX64
- linuxArm64
- linuxX64
- mingwX64
- wasmJs (potential future candidate)
- jvm (potential future candidate)
