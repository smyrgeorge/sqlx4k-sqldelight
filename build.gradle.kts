group = "io.github.smyrgeorge"
version = "0.20.0"

plugins {
    alias(libs.plugins.dokka)
    alias(libs.plugins.pubhish) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
}

repositories {
    mavenCentral()
}

subprojects {
    group = rootProject.group
    version = rootProject.version

    repositories {
        mavenCentral()
    }

    // Dokka config
    run {
        // Exclude examples.
        if (projectDir.path.contains("/examples")) return@run
        // Run with ./gradlew :dokkaHtmlMultiModule
        apply(plugin = "org.jetbrains.dokka")
    }
}