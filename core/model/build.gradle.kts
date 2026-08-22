plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        // Модуль обязан оставаться переносимым: никаких Android- и java.*-зависимостей,
        // чтобы в v2 он поехал на Mac через Kotlin Multiplatform без переписывания.
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }
}

dependencies {
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}

tasks.test { useJUnit() }
