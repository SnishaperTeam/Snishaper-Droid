plugins {
    id("com.android.application")
    // AGP 9 has built-in Kotlin support: do not apply org.jetbrains.kotlin.android here.
    id("org.jetbrains.kotlin.plugin.compose")
}

/**
 * Debug APK 只打包一个 ABI（默认 arm64-v8a），显著缩短本地构建/安装时间。
 * MuMu 模拟器为 Android 15 x86_64：冒烟测试时用
 *   ./gradlew :app:assembleDebug -PdebugAbi=x86_64
 * 覆盖。Release 不设置 abiFilters，打包 AAR 中的全部 ABI
 * （arm64-v8a / armeabi-v7a / x86_64）。
 */
val debugAbi: String = (findProperty("debugAbi") as String?)
    ?.trim()
    ?.takeUnless { it.isEmpty() }
    ?: "arm64-v8a"

android {
    namespace = "com.snishaper.android"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.snishaper.android"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.29.0"

        // Also expose the version to the UI layer (About / Settings rows).
        buildConfigField("String", "CORE_VERSION", "\"core v0.4.0\"")
    }

    buildTypes {
        debug {
            // Local debug builds ship one ABI only (arm64-v8a by default).
            // MuMu is x86_64 Android 15: use `-PdebugAbi=x86_64` for the smoke test.
            ndk {
                abiFilters.clear()
                abiFilters.add(debugAbi)
            }
        }
        release {
            // Release keeps every ABI provided by the gomobile AAR
            // (arm64-v8a / armeabi-v7a / x86_64): no abiFilters on purpose.
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
        jniLibs {
            // Keep 16 KB page alignment of the gomobile .so files (default, pinned on purpose).
            useLegacyPackaging = false
        }
    }
}

// Built-in Kotlin defaults `kotlin.compilerOptions.jvmTarget` to
// `android.compileOptions.targetCompatibility` (17), so no explicit kotlin {} block is needed.

dependencies {
    // gomobile AAR is produced by the go-bridge task. The skeleton must build without it,
    // so the dependency is declared conditionally.
    val snishaperCoreAar = file("libs/snishaper-core.aar")
    if (snishaperCoreAar.exists()) {
        implementation(files(snishaperCoreAar))
    }

    implementation("androidx.core:core-ktx:1.19.1")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.datastore:datastore-preferences:1.2.1")
    implementation("androidx.navigation:navigation-compose:2.10.2")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")

    implementation(platform("androidx.compose:compose-bom:2026.09.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
