plugins {
    id("buddy.android.library")
    id("buddy.android.hilt")
}

android {
    namespace = "com.buddy.aios.core.security"
}

dependencies {
    implementation(project(":core:common"))

    implementation(libs.security.crypto)
    implementation(libs.biometric.ktx)
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit5.api)
    testRuntimeOnly(libs.junit5.engine)
    testImplementation(libs.mockk.core)
    testImplementation(libs.robolectric)
}

tasks.withType<Test> {
    useJUnitPlatform()
}
