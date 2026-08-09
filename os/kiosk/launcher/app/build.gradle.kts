import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

/**
 * Firma de release. La clave nunca vive en el repositorio: se toma de
 * `keystore.properties` (ignorado por git) o de variables de entorno en CI.
 * Sin clave disponible el build de release cae a la firma de depuracion, de
 * modo que `assembleRelease` siempre produce un APK instalable para pruebas.
 */
val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps = Properties().apply {
    if (keystorePropsFile.exists()) keystorePropsFile.inputStream().use { load(it) }
}

fun secret(key: String, env: String): String? =
    keystoreProps.getProperty(key) ?: System.getenv(env)

val storeFilePath = secret("storeFile", "DIAGNOSTRUCT_KEYSTORE")
val hasReleaseKey = storeFilePath != null && rootProject.file(storeFilePath).exists()

android {
    namespace = "com.diagnostruct.os"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.diagnostruct.os"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"

        // Paquete de la aplicacion unica autorizada en el kiosco.
        buildConfigField("String", "APP_PACKAGE", "\"com.ingnieto.diagnostruct\"")
        // Manifiesto de versiones publicado en el repositorio de releases.
        buildConfigField(
            "String",
            "VERSION_MANIFEST_URL",
            "\"https://raw.githubusercontent.com/ingenieronieto/DIAGNOSTRUCT-releases/main/version.json\""
        )
    }

    signingConfigs {
        if (hasReleaseKey) {
            create("release") {
                storeFile = rootProject.file(storeFilePath!!)
                storePassword = secret("storePassword", "DIAGNOSTRUCT_KEYSTORE_PASSWORD")
                keyAlias = secret("keyAlias", "DIAGNOSTRUCT_KEY_ALIAS")
                keyPassword = secret("keyPassword", "DIAGNOSTRUCT_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = if (hasReleaseKey) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
        debug {
            applicationIdSuffix = ".debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        buildConfig = true
        viewBinding = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.lifecycle.service)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.kotlinx.coroutines.android)
}
