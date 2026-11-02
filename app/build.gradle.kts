plugins {
    id("com.android.application")
    // AGP 9 has built-in Kotlin support: do not apply org.jetbrains.kotlin.android here.
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
    // AndroidX libraries used here require compileSdk >= 37; the installed platform
    // on this machine (and in CI) is android-37.2. targetSdk stays 36 for runtime
    // behaviour compatibility.
    compileSdk {
        version = release(37) {
            minorApiLevel = 2
        }
    }

    defaultConfig {
        applicationId = "com.snishaper.android"
        minSdk = 24
        targetSdk = 36
        // The release workflow's bot rewrites this line on every manual
        // dispatch (see .github/workflows/release.yml). versionCode derives
        // from versionName so sideload installers (MIUI et al.) always see a
        // monotonic code; prerelease suffixes keep the base code.
        versionName = "1.30.0"
        versionCode = (versionName ?: "0.0.0").substringBefore('-').split(".").let { parts ->
            (parts.getOrElse(0) { "0" }.toIntOrNull() ?: 0) * 1_000_000 +
                (parts.getOrElse(1) { "0" }.toIntOrNull() ?: 0) * 1_000 +
                (parts.getOrElse(2) { "0" }.filter { it.isDigit() }.ifEmpty { "0" }.toIntOrNull() ?: 0)
        }

        // Also expose the version to the UI layer (About / Settings rows).
        buildConfigField("String", "CORE_VERSION", "\"core v0.8.3\"")
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
    implementation("androidx.activity:activity-ktx:1.13.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")
}
