import java.util.Properties

plugins {
    id("buddy.android.application")
    id("buddy.android.hilt")
    alias(libs.plugins.google.services)
    alias(libs.plugins.firebase.crashlytics)
}

android {
    namespace = "com.buddy.aios"

    defaultConfig {
        applicationId = "com.buddy.aios"
        versionCode = 1
        versionName = "1.0.0"

        // ── AI Provider Configuration ─────────────────────────────────────────
        // Read GEMINI_API_KEY from rootProject local.properties, project property, or environment variable.
        val localProperties = Properties()
        val localPropertiesFile = rootProject.file("local.properties")
        if (localPropertiesFile.exists()) {
            localPropertiesFile.inputStream().use { localProperties.load(it) }
        }
        val rawGeminiKey = localProperties.getProperty("GEMINI_API_KEY")
            ?: project.findProperty("GEMINI_API_KEY")?.toString()
            ?: System.getenv("GEMINI_API_KEY")
            ?: ""
        val geminiKey = rawGeminiKey.trim().removeSurrounding("\"")
        buildConfigField("String", "GEMINI_API_KEY", "\"$geminiKey\"")

        // Path to the on-device Gemma model file (.task).
        buildConfigField("String", "GEMMA_MODEL_PATH", "\"\"")
    }

    buildFeatures {
        buildConfig = true
    }
}


dependencies {
    // Core modules
    implementation(project(":core:common"))
    implementation(project(":core:domain"))
    implementation(project(":core:data"))
    implementation(project(":core:ai"))
    implementation(project(":core:ui"))
    implementation(project(":core:analytics"))

    // Feature modules
    implementation(project(":feature:chat"))
    implementation(project(":feature:home"))
    implementation(project(":feature:memory"))
    implementation(project(":feature:settings"))
    implementation(project(":feature:onboarding"))

    // Workers
    implementation(project(":workers"))

    // Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.bundles.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.hilt.navigation.compose)

    // Firebase
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.crashlytics)
    implementation(libs.firebase.analytics)
    implementation(libs.firebase.auth)

    // Core AndroidX & WorkManager
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.work.runtime.ktx)
    implementation(libs.hilt.work)
    ksp(libs.hilt.compiler.androidx)
}
