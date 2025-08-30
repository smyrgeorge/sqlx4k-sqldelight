package io.github.smyrgeorge.sqlx4k.publish

import com.vanniktech.maven.publish.KotlinMultiplatform
import com.vanniktech.maven.publish.MavenPublishBaseExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

@Suppress("unused")
class PublishConventions : Plugin<Project> {

    private val descriptions: Map<String, String> = mapOf(
        "sqlx4k-sqldelight" to "Sqldelight support for sqlx4k.",
        "sqlx4k-sqldelight-dialect-mysql" to "Sqldelight support for sqlx4k (MySQL dialect).",
        "sqlx4k-sqldelight-dialect-postgres" to "Sqldelight support for sqlx4k (PostgreSQL dialect).",
    )

    override fun apply(project: Project) {
        project.plugins.apply("com.vanniktech.maven.publish")
        project.extensions.configure<MavenPublishBaseExtension> {
            // sources publishing is always enabled by the Kotlin Multiplatform plugin
            configure(
                KotlinMultiplatform(
                    // whether to publish a sources jar
                    sourcesJar = true,
                )
            )
            coordinates(
                groupId = project.group as String,
                artifactId = project.name,
                version = project.version as String
            )

            pom {
                name.set(project.name)
                description.set(descriptions[project.name] ?: error("Missing description for $project.name"))
                url.set("https://github.com/smyrgeorge/sqlx4k-sqldelight")

                licenses {
                    license {
                        name.set("MIT License")
                        url.set("https://github.com/smyrgeorge/sqlx4k-sqldelight/blob/main/LICENSE")
                    }
                }

                developers {
                    developer {
                        id.set("smyrgeorge")
                        name.set("Yorgos S.")
                        email.set("smyrgoerge@gmail.com")
                        url.set("https://smyrgeorge.github.io/")
                    }
                }

                scm {
                    url.set("https://github.com/smyrgeorge/sqlx4k-sqldelight")
                    connection.set("scm:git:https://github.com/smyrgeorge/sqlx4k-sqldelight.git")
                    developerConnection.set("scm:git:git@github.com:smyrgeorge/sqlx4k-sqldelight.git")
                }
            }

            // Configure publishing to Maven Central
            publishToMavenCentral()

            // Enable GPG signing for all publications
            signAllPublications()
        }
    }
}
