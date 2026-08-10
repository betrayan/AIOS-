plugins {
    id("buddy.android.library")
    id("buddy.android.hilt")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.buddy.aios.core.network"
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:security"))

    implementation(libs.bundles.retrofit)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit5.api)
    testRuntimeOnly(libs.junit5.engine)
    testImplementation(libs.mockk.core)
    testImplementation(libs.kotlinx.coroutines.test)
}

tasks.withType<Test> {
    useJUnitPlatform()
}
