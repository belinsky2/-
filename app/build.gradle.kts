import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.compiler)
}

/**
 * Подпись релизным ключом. Android принимает обновление только от APK,
 * подписанного тем же ключом, — смена ключа означала бы переустановку
 * с потерей всех данных. Поэтому ключ фиксируется с самой первой сборки.
 *
 * Локально: keystore.properties (в .gitignore).
 * В CI: те же значения приезжают переменными окружения из GitHub Secrets.
 */
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        keystorePropertiesFile.inputStream().use { load(it) }
    }
}

fun secret(key: String, env: String): String? =
    keystoreProperties.getProperty(key) ?: System.getenv(env)

val releaseStoreFile = secret("storeFile", "PUNCHLINE_KEYSTORE_FILE")
val hasReleaseSigning = releaseStoreFile != null && rootProject.file(releaseStoreFile).exists()

android {
    namespace = "ru.punchline.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "ru.punchline.workbook"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0-m0"
    }

    signingConfigs {
        // Постоянный debug-ключ вместо того, что Android-плагин генерирует сам.
        // На чистой машине сборки он создаётся заново каждый раз, подпись
        // не совпадает с уже установленной версией, и обновление отклоняется
        // сообщением «Приложение не установлено».
        getByName("debug") {
            storeFile = rootProject.file("app/debug-signing.jks")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }

        if (hasReleaseSigning) {
            create("release") {
                storeFile = rootProject.file(releaseStoreFile!!)
                storePassword = secret("storePassword", "PUNCHLINE_KEYSTORE_PASSWORD")
                keyAlias = secret("keyAlias", "PUNCHLINE_KEY_ALIAS")
                keyPassword = secret("keyPassword", "PUNCHLINE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        debug {
            applicationIdSuffix = ".debug"
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:data"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.material3)
    implementation(libs.compose.animation)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)
}

/**
 * Ни одной строки, видимой пользователю, в коде: иначе добавление второго языка
 * превращается в вычёсывание строк по всему проекту.
 */
tasks.register("checkNoHardcodedCyrillic") {
    group = "verification"
    val sources = fileTree("src/main/kotlin") { include("**/*.kt") }
    inputs.files(sources)
    doLast {
        val offenders = sources.files.flatMap { file ->
            file.readLines().withIndex().mapNotNull { (i, line) ->
                val code = line.substringBefore("//")
                val literals = Regex("\"([^\"\\\\]|\\\\.)*\"").findAll(code).map { it.value }
                if (literals.any { it.any { ch -> ch in 'а'..'я' || ch in 'А'..'Я' || ch == 'ё' || ch == 'Ё' } }) {
                    "${file.relativeTo(projectDir)}:${i + 1}: ${line.trim()}"
                } else null
            }
        }
        if (offenders.isNotEmpty()) {
            throw GradleException(
                "Cyrillic string literals must live in strings.xml:\n" + offenders.joinToString("\n")
            )
        }
    }
}

tasks.named("check") { dependsOn("checkNoHardcodedCyrillic") }
