import com.vanniktech.maven.publish.KotlinMultiplatform
import com.vanniktech.maven.publish.MavenPublishBaseExtension
import com.vanniktech.maven.publish.SourcesJar

plugins {
    id("com.vanniktech.maven.publish")
}

val descriptions: Map<String, String> = mapOf(
    "sqlx4k-sqldelight" to "Sqldelight support for sqlx4k.",
    "sqlx4k-sqldelight-dialect-mysql" to "Sqldelight support for sqlx4k (MySQL dialect).",
    "sqlx4k-sqldelight-dialect-postgres" to "Sqldelight support for sqlx4k (PostgreSQL dialect).",
    "sqlx4k-sqldelight-dialect-sqlite" to "Sqldelight support for sqlx4k (SQLite dialect).",
)

extensions.configure<MavenPublishBaseExtension> {
    configure(
        KotlinMultiplatform(
            sourcesJar = SourcesJar.Sources()
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
                email.set("smyrgeorge@gmail.com")
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
