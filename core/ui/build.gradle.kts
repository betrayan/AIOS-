import org.gradle.api.tasks.testing.Test

plugins {
    id("buddy.android.library")
    id("buddy.android.hilt")
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.buddy.aios.core.ui"

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:domain"))

    api(platform(libs.androidx.compose.bom))
    api(libs.bundles.compose)
    api(libs.coil.compose)
    api(libs.kotlinx.coroutines.android)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    testImplementation(libs.junit5.api)
    testRuntimeOnly(libs.junit5.engine)
    testImplementation(libs.mockk.core)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
