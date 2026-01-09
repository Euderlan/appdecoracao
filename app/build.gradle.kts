plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "br.ufma.decoracaoar"
    compileSdk = 36

    defaultConfig {
        applicationId = "br.ufma.decoracaoar"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Garante que modelos 3D não sejam compactados dentro do APK/AAB
    androidResources {
        noCompress += listOf("glb", "gltf", "bin")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
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
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.2.0")

    // ARCore (Google Play Services for AR)
    implementation("com.google.ar:core:1.51.0")

    // SceneView (ARCore + Filament)
    // Observação: a API do SceneView mudou bastante entre versões.
    // Este projeto usa a linha 2.x (ArSceneView = ARSceneView).
    implementation("io.github.sceneview:arsceneview:2.3.1")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")

}
