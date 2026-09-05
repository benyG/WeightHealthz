import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

/** Même mécanisme que dans `core-ai` — voir le commentaire là-bas et DEPLOYMENT.md §4. */
fun secretOrEmpty(key: String): String {
    val properties = Properties()
    val file = rootProject.file("local.properties")
    if (file.exists()) {
        file.inputStream().use { stream -> properties.load(stream) }
    }
    return properties.getProperty(key) ?: System.getenv(key) ?: ""
}

android {
    namespace = "com.forge.core.sync"
    compileSdk = 35

    defaultConfig {
        minSdk = 30

        buildConfigField(
            "String",
            "GOOGLE_CALENDAR_OAUTH_CLIENT_ID",
            "\"${secretOrEmpty("GOOGLE_CALENDAR_OAUTH_CLIENT_ID")}\"",
        )
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

// L'URL du webhook Alexa n'est pas ici : elle est saisie à l'onboarding (SPEC.md §5.1) et vit
// dans les préférences de l'app, pas dans BuildConfig. Le fournisseur reste à trancher
// (DEPLOYMENT.md §11) avant l'implémentation de la phase 4.
dependencies {
    implementation(project(":core-domain"))

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)
    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)

    testImplementation(libs.junit4)
}
