import java.io.File
import java.util.Properties

plugins {
    id("com.android.application")
}

val debugAbi: String = (findProperty("debugAbi") as String?)
    ?.trim()
    ?.takeUnless { it.isEmpty() }
    ?: "arm64-v8a"

val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        keystorePropertiesFile.inputStream().use { load(it) }
    }
}

fun signingValue(propertyKey: String, envKey: String): String? =
    (System.getenv(envKey) ?: keystoreProperties.getProperty(propertyKey))
        ?.trim()
        ?.takeIf { it.isNotEmpty() }

val releaseStorePath = signingValue("storeFile", "SNISHAPER_KEYSTORE")
val releaseStorePassword = signingValue("storePassword", "SNISHAPER_KEYSTORE_PASSWORD")
val releaseKeyAlias = signingValue("keyAlias", "SNISHAPER_KEY_ALIAS")
val releaseKeyPassword = signingValue("keyPassword", "SNISHAPER_KEY_PASSWORD")

val releaseStoreFile: File? = releaseStorePath?.let { path ->
    val candidate = File(path)
    if (candidate.isAbsolute) candidate else rootProject.file(path)
}
val releaseSigningReady: Boolean = releaseStoreFile?.exists() == true &&
    !releaseStorePassword.isNullOrEmpty() &&
    !releaseKeyAlias.isNullOrEmpty() &&
    !releaseKeyPassword.isNullOrEmpty()

android {
    namespace = "com.snishaper.android"
    compileSdk {
        version = release(37) {
            minorApiLevel = 2
        }
    }

    defaultConfig {
        applicationId = "com.snishaper.android"
        minSdk = 24
        targetSdk = 36
        versionName = "1.30.0"
        versionCode = (versionName ?: "0.0.0").substringBefore('-').split(".").let { parts ->
            (parts.getOrElse(0) { "0" }.toIntOrNull() ?: 0) * 1_000_000 +
                (parts.getOrElse(1) { "0" }.toIntOrNull() ?: 0) * 1_000 +
                (parts.getOrElse(2) { "0" }.filter { it.isDigit() }.ifEmpty { "0" }.toIntOrNull() ?: 0)
        }
    }

    signingConfigs {
        if (releaseSigningReady) {
            create("release") {
                storeFile = releaseStoreFile
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
                enableV1Signing = false
                enableV2Signing = true
                enableV3Signing = true
                enableV4Signing = true
            }
        }
    }

    buildTypes {
        debug {
            ndk {
                abiFilters.clear()
                abiFilters.add(debugAbi)
            }
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release")
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
            useLegacyPackaging = false
        }
    }
}

dependencies {
    val snishaperCoreAar = file("libs/snishaper-core.aar")
    if (snishaperCoreAar.exists()) {
        implementation(files(snishaperCoreAar))
    }

    implementation("androidx.core:core-ktx:1.19.1")
    implementation("androidx.activity:activity-ktx:1.13.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")
}

tasks.register("signingInfo") {
    group = "help"
    doLast {
        if (releaseSigningReady) {
            println("release signing : ready")
            println("keystore        : ${releaseStoreFile?.absolutePath}")
            println("alias           : $releaseKeyAlias")
        } else {
            println("release signing : MISSING")
            println("source          : keystore.properties storeFile/storePassword/keyAlias/keyPassword")
            println("                  or SNISHAPER_KEYSTORE / SNISHAPER_KEYSTORE_PASSWORD / SNISHAPER_KEY_ALIAS / SNISHAPER_KEY_PASSWORD")
            println("create one      : pwsh -File scripts/init-keystore.ps1")
        }
    }
}
