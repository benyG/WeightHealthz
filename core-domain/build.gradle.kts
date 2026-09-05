import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Module Kotlin/JVM pur, volontairement sans plugin Android : la règle de CLAUDE.md
// ("core-domain ne doit jamais importer android.*") devient une erreur de compilation
// plutôt qu'une convention à faire respecter en relecture.
plugins {
    alias(libs.plugins.kotlin.jvm)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(kotlin("test"))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
}
