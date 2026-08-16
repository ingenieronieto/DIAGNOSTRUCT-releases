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
        // SHA-256 del certificado con el que se firma DIAGNOSTRUCT.
        //
        // En una actualizacion Android ya exige que la firma coincida con la de
        // lo instalado, pero en la PRIMERA instalacion no hay con que comparar:
        // sin este anclaje, quien controlase la URL de descarga colocaria un APK
        // cualquiera con ese nombre de paquete, y el kiosco le concederia camara
        // y ubicacion de antemano. Dejarlo vacio desactiva la comprobacion.
        buildConfigField(
            "String",
            "APP_SIGNATURE_SHA256",
            "\"fcf11d36ec6ca25b0208eaa7147b84f503ec24b479f02ae0ea7696468f2b8b60\""
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

    testOptions {
        unitTests {
            // Las clases de android.jar son stubs que lanzan al invocarse. Las
            // pruebas de aqui solo cubren logica pura, pero este ajuste evita
            // que una llamada incidental a la plataforma tumbe la ejecucion.
            isReturnDefaultValues = true
        }
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

    testImplementation(libs.junit)
    testImplementation(libs.json)
}
