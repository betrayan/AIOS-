plugins {
    id("buddy.android.library")
    id("buddy.android.hilt")
}

android {
    namespace = "com.buddy.aios.core.ai"
}

dependencies {
    implementation(project(":core:domain"))
    implementation(project(":core:common"))
    implementation(project(":core:network"))
    implementation(project(":core:analytics"))

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.bundles.retrofit)

    // MediaPipe LLM Inference (on-device AI)
    implementation("com.google.mediapipe:tasks-genai:0.10.14")

    testImplementation(libs.junit5.api)
    testRuntimeOnly(libs.junit5.engine)
    testImplementation(libs.mockk.core)
    testImplementation(libs.turbine)
    testImplementation(libs.kotlinx.coroutines.test)
}

tasks.withType<Test> {
    useJUnitPlatform()
}
