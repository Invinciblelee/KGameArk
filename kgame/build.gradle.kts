import com.vanniktech.maven.publish.DeploymentValidation
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.mavenPublish)
}

kotlin {
    android {
        namespace = "com.kgame.engine"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
    }

    listOf(iosArm64(), iosSimulatorArm64()).forEach {
        it.binaries.framework { baseName = "KGameEngineKit" }
    }

    jvm()

    js { outputModuleName = "KGameEngineKit"; browser() }

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs { outputModuleName = "KGameEngineKit"; browser() }

    compilerOptions {
        languageVersion.set(KotlinVersion.KOTLIN_2_2)
        apiVersion.set(KotlinVersion.KOTLIN_2_2)
        freeCompilerArgs.addAll(
            "-Xexpect-actual-classes",
            "-opt-in=kotlin.time.ExperimentalTime",
            "-opt-in=kotlin.uuid.ExperimentalUuidApi",
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
            "-opt-in=androidx.compose.ui.ExperimentalComposeUiApi",
            "-opt-in=org.jetbrains.compose.resources.ExperimentalResourceApi",
        )
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.material.icons)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.ui.tooling.preview)
            implementation(libs.androidx.lifecycle.runtime)
            implementation(libs.androidx.lifecycle.common)
            implementation(libs.androidx.navigation3.ui)
            implementation(libs.androidx.navigationevent)
            implementation(libs.androidx.collection)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.atomicfu)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.ksoup)
        }
        androidMain.dependencies {
            implementation(libs.kotlinx.coroutines.android)
        }
        jvmMain.dependencies {
            implementation(libs.kotlinx.coroutines.swing)
            implementation(libs.soundlibs.tritonus.share)
            implementation(libs.soundlibs.mp3spi)
            implementation(libs.soundlibs.vorbisspi)
        }
        webMain.dependencies {
            implementation(libs.kotlin.browser)
            implementation(npm("pako", "2.1.0"))
        }
        commonTest.dependencies { implementation(libs.kotlin.test) }
        jvmTest.dependencies {
            implementation(libs.kotlin.testJunit)
            implementation(libs.junit)
        }
    }
}

mavenPublishing {
    publishToMavenCentral()

    if (!project.gradle.startParameter.taskNames.any { it.contains("MavenLocal") }) {
        signAllPublications()
    }

    coordinates(
        groupId = "com.kgame",
        artifactId = "kgame-engine",
        version = "1.0.0-alpha01"
    )

    pom {
        name = "KGame Engine"
        description = "A Kotlin Multiplatform game engine built with Compose Multiplatform"
        inceptionYear = "2026"
        url = "https://github.com/Invinciblelee/KGameArk"

        licenses {
            license {
                name = "The Apache License, Version 2.0"
                url = "https://www.apache.org/licenses/LICENSE-2.0.txt"
                distribution = "https://www.apache.org/licenses/LICENSE-2.0.txt"
            }
        }

        developers {
            developer {
                id = "Invinciblelee"
                name = "LiZhanPing"
                url = "https://github.com/Invinciblelee"
                email = "481314821@qq.com"
            }
        }

        scm {
            url = "https://github.com/Invinciblelee/KGameArk"
            connection = "scm:git:git://github.com/Invinciblelee/KGameArk.git"
            developerConnection = "scm:git:ssh://git@github.com:Invinciblelee/KGameArk.git"
        }
    }
}