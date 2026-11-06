plugins {
    id("com.android.application") version "8.1.1"
    id("org.jetbrains.kotlin.android") version "1.9.24"
    id("com.google.gms.google-services") version "4.4.0" // Necesario para Firebase
}

android {
    namespace = "com.closetvr.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.closetvr.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    buildFeatures {
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    // Core / Compose
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.activity:activity-compose:1.9.1")
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    // Navegación entre pantallas
    implementation("androidx.navigation:navigation-compose:2.7.7")

    // ViewModel + Compose
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.4")

    // collectAsStateWithLifecycle(): estados de los ViewModels que respetan el ciclo de vida
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.4")

    // Splash Screen API (arranque sin parpadeo blanco, compatible con Android 8+)
    implementation("androidx.core:core-splashscreen:1.0.1")

    // Networking: Retrofit + Gson para hablar con el backend
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    // Carga de imágenes de productos desde URL
    implementation("io.coil-kt:coil-compose:2.6.0")

    // CameraX: cámara funcional para capturar prendas
    val cameraxVersion = "1.3.4"
    implementation("androidx.camera:camera-core:$cameraxVersion")
    implementation("androidx.camera:camera-camera2:$cameraxVersion")
    implementation("androidx.camera:camera-lifecycle:$cameraxVersion")
    implementation("androidx.camera:camera-view:$cameraxVersion")

    // Guardar el token de sesión de forma persistente
    implementation("androidx.datastore:datastore-preferences:1.0.0")

    // Permisos en Compose (cámara)
    implementation("com.google.accompanist:accompanist-permissions:0.34.0")

    debugImplementation("androidx.compose.ui:ui-tooling")

    // Import the Firebase BoM (Esta línea asegura que todas las librerías sean compatibles)
    implementation(platform("com.google.firebase:firebase-bom:33.1.2"))

    // Esta es la librería de Auth para tu Login
    implementation("com.google.firebase:firebase-auth")

    // Credential Manager para Google Sign-In
    implementation("androidx.credentials:credentials:1.3.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.3.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")
}