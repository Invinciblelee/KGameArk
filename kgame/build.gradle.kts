import com.android.build.gradle.tasks.MergeSourceSetFolders
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.composeHotReload)
}

// Skiko Native Workaround
//val skikoNativeArm64: Configuration by configurations.creating
//val skikoNativeX64: Configuration by configurations.creating
//val skikoJniDir = "$projectDir/src/androidMain/jniLibs"
//
//val unzipSkikoNativeArm64 = tasks.register<Copy>("unzipSkikoNativeArm64") {
//    from(skikoNativeArm64.map { zipTree(it) })
//    into(file("$skikoJniDir/arm64-v8a"))
//    include("*.so")
//}
//
//val unzipSkikoNativeX64 = tasks.register<Copy>("unzipSkikoNativeX64") {
//    from(skikoNativeX64.map { zipTree(it) })
//    into(file("$skikoJniDir/x86_64"))
//    include("*.so")
//}
//
//project.tasks.withType<MergeSourceSetFolders>().configureEach {
//    dependsOn(unzipSkikoNativeArm64)
//    dependsOn(unzipSkikoNativeX64)
//}

kotlin {
    androidLibrary {
        namespace = "com.kgame.engine"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
    }

    listOf(iosX64(), iosArm64(), iosSimulatorArm64()).forEach {
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
//            implementation(libs.skiko)
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

//dependencies {
//    skikoNativeArm64(libs.skiko.android.runtime.arm64)
//    skikoNativeX64(libs.skiko.android.runtime.x64)
//}