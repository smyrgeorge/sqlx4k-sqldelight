import io.github.smyrgeorge.sqlx4k.multiplatform.Utils
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

plugins {
    id("org.jetbrains.kotlin.multiplatform")
}

extensions.configure<KotlinMultiplatformExtension> {
    val targets = Utils.targetsOf(project)
    val availableTargets = mapOf(
        Pair("jvm") { jvm() },
        Pair("iosArm64") { iosArm64() },
        Pair("androidNativeArm64") { androidNativeArm64() },
        Pair("androidNativeX64") { androidNativeX64() },
        Pair("macosArm64") { macosArm64() },
        Pair("linuxArm64") { linuxArm64() },
        Pair("linuxX64") { linuxX64() },
        Pair("mingwX64") { mingwX64() },
    )

    targets.forEach {
        println("Enabling target $it")
        availableTargets[it]?.invoke()
    }

    applyDefaultHierarchyTemplate()
}
