plugins {
    alias(libs.plugins.androidApplication)
}

android {
    namespace = "com.example.kgame"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.example.kgame"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0"
    }

    packaging {
        resources {
            excludes += listOf(
                "META-INF/{AL2.0,LGPL2.1}",
                "META-INF/INDEX.LIST",
                "META-INF/io.netty.versions.properties",
                "META-INF/LICENSE.md",
                "META-INF/NOTICE.md"
            )
        }
    }
    signingConfigs {
        create("signingConfig") {
            keyAlias = "kmp"
            keyPassword = "20251211"
            storeFile = file("androidApp.jks")
            storePassword = "20251211"
        }
    }
    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("signingConfig")

            isMinifyEnabled = false
        }
        release {
            signingConfig = signingConfigs.getByName("signingConfig")

            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

dependencies {
    implementation(project(":shared"))
    implementation(libs.androidx.activity.compose)
    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.bundles.ktor.server)
    implementation(libs.ktor.serialization.json)

    implementation(libs.kotlin.css)

    implementation(libs.logback.android)
    implementation(libs.slf4j.api)
}

val cleanWasmAssets = tasks.register<Delete>("cleanWasmAssets") {
    delete("src/main/resources/productionExecutable")
}

tasks.register<Copy>("copyWasmAssets") {
    dependsOn(cleanWasmAssets)

    from("../webApp/build/dist/wasmJs")
    into("src/main/resources")
    include("**/*")
}

tasks.getByName("preBuild") {
    dependsOn("copyWasmAssets")
}