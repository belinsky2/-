plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

/**
 * Формат хранилища: адресуемые по содержимому блобы и архив переноса.
 *
 * Чистый JVM без Android — по двум причинам. Во-первых, это ровно тот код,
 * который в v2 достанется приложению на Mac без изменений: формат обмена
 * обязан быть общим. Во-вторых, его можно собрать и протестировать там,
 * где Android SDK недоступен.
 */
kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.junit)
}

tasks.test { useJUnit() }
