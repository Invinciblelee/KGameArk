import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    jvm()

    sourceSets {
        jvmMain.dependencies {
            implementation(project(":shared"))

            implementation(libs.bundles.ktor.server)
            implementation(libs.ktor.serialization.json)
            implementation(libs.logback.classic)
        }
    }
}

compose.desktop {
    application {
        mainClass = "com.example.kgame.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "com.example.kgame"
            packageVersion = "1.0.0"
            jvmArgs(
                "-Dapple.awt.application.appearance=system"
            )
            buildTypes.release.proguard {
                isEnabled.set(false)
            }
        }
    }
}