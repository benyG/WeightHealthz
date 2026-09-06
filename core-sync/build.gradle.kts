import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.forge.core.sync"
    compileSdk = 35

    defaultConfig {
        minSdk = 30
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests {
            // Seul `android.util.Log` traverse le framework dans ces tests.
            isReturnDefaultValues = true
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

// Aucun secret dans ce module : l'agenda passe par le fournisseur du système (aucun jeton
// OAuth), et le code d'accès du relais vocal est saisi à l'onboarding (SPEC.md §5.1), stocké
// dans les préférences de l'app — jamais compilé dans l'APK (DEPLOYMENT.md §11).
dependencies {
    implementation(project(":core-domain"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)
    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)

    testImplementation(libs.junit4)
    testImplementation(libs.kotlinx.coroutines.test)
    // Le relais vocal est un client HTTP : un serveur local est la seule façon d'en vérifier
    // le corps de requête et le traitement des codes de retour sans appeler le vrai service.
    testImplementation(libs.okhttp.mockwebserver)
}
