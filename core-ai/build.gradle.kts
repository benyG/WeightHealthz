import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

/**
 * Lit un secret depuis `local.properties` (poste de dev) puis depuis l'environnement (CI).
 * Absent des deux, la valeur est vide : le build reste vert sans clé — c'est ce qui permet à la
 * CI de compiler sans secret, l'appel réseau échouant explicitement à l'exécution.
 * Voir DEPLOYMENT.md §4.
 */
fun secretOrEmpty(key: String): String {
    val properties = Properties()
    val file = rootProject.file("local.properties")
    if (file.exists()) {
        file.inputStream().use { stream -> properties.load(stream) }
    }
    return properties.getProperty(key) ?: System.getenv(key) ?: ""
}

android {
    namespace = "com.forge.core.ai"
    compileSdk = 35

    defaultConfig {
        minSdk = 30

        buildConfigField("String", "GEMINI_API_KEY", "\"${secretOrEmpty("GEMINI_API_KEY")}\"")
        // Déclarée dès maintenant, consommée seulement en phase 2 (SPEC.md §9).
        buildConfigField("String", "DEEPGRAM_API_KEY", "\"${secretOrEmpty("DEEPGRAM_API_KEY")}\"")
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests {
            // Les tests sont du Kotlin pur ; seul `android.util.Log` traverse le framework et
            // doit rendre une valeur par défaut au lieu de lever.
            isReturnDefaultValues = true
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":core-domain"))

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization.converter)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging.interceptor)
    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)

    testImplementation(libs.junit4)
    testImplementation(libs.kotlinx.coroutines.test)
}
