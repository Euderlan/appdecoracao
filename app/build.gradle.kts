// Bloco de plugins que define quais ferramentas de build serão usadas
plugins {
    // Plugin para aplicações Android
    alias(libs.plugins.android.application)
    // Plugin para suporte a linguagem Kotlin
    alias(libs.plugins.kotlin.android)
}

// Configurações específicas do Android
android {
    // Namespace do pacote da aplicação (identificador único)
    namespace = "com.example.decoracao"

    // SDK de compilação - versão do Android usada para compilar o código
    // 36 corresponde ao Android 15
    compileSdk = 36

    // Configurações padrão do projeto
    defaultConfig {
        // ID único da aplicação (usado na Google Play Store)
        applicationId = "com.example.decoracao"

        // SDK mínimo suportado
        // 24 = Android 7.0 (API 24)
        // Dispositivos com versão menor não conseguem instalar o app
        minSdk = 24

        // SDK alvo
        // 36 = Android 15
        // Versão do Android para qual o app foi otimizado
        targetSdk = 36

        // Código da versão (incrementado a cada atualização)
        versionCode = 1

        // Versão exibida para o usuário
        versionName = "1.0"

        // Executor de testes instrumentados (para testes no dispositivo)
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Configuração de recursos Android
    androidResources {
        // Define que arquivos GLB, GLTF e BIN não devem ser comprimidos
        // Isso preserva a integridade dos modelos 3D
        noCompress += listOf("glb", "gltf", "bin")
    }

    // Tipos de build (debug e release)
    buildTypes {
        // Configuração de build para release (produção)
        release {
            // isMinifyEnabled = false desabilita ofuscação de código
            // Se true, usa ProGuard para reduzir tamanho e ofuscar
            isMinifyEnabled = false

            // Arquivos de configuração do ProGuard
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    // Configuração do compilador Java
    compileOptions {
        // Versão de Java usada como fonte (escrita do código)
        sourceCompatibility = JavaVersion.VERSION_17

        // Versão de Java para a qual o código será compilado
        targetCompatibility = JavaVersion.VERSION_17
    }

    // Configuração do compilador Kotlin
    kotlin {
        compilerOptions {
            // Define a versão da JVM (Java Virtual Machine) como alvo
            // JVM_17 = Java 17
            jvmTarget.set(
                org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
            )
        }
    }
}

// Bloco de dependências (bibliotecas externas usadas no projeto)
dependencies {
    // AndroidX Core com suporte a Kotlin Extensions
    implementation(libs.androidx.core.ktx)

    // AppCompat - compatibilidade com versões antigas do Android
    implementation("androidx.appcompat:appcompat:1.7.0")

    // Material Design - componentes UI modernos (como MaterialButton)
    implementation("com.google.android.material:material:1.12.0")

    // ConstraintLayout - layout flexível e eficiente para telas responsivas
    implementation("androidx.constraintlayout:constraintlayout:2.2.0")

    // ARCore - biblioteca principal do Google para Realidade Aumentada
    implementation("com.google.ar:core:1.51.0")

    // SceneView - wrapper que facilita o uso de ARCore com 3D
    // Fornece ARSceneView, ModelNode, AnchorNode, etc
    implementation("io.github.sceneview:arsceneview:2.3.1")

    // Testes unitários (testes locais que não precisam de dispositivo)
    testImplementation(libs.junit)

    // Testes instrumentados (testes que rodam no dispositivo/emulador)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}