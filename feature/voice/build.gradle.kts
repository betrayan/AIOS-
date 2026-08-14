plugins {
    id("buddy.android.feature")
    id("buddy.android.hilt")
}

android {
    namespace = "com.buddy.aios.feature.voice"
}

dependencies {
    implementation(project(":core:domain"))
    implementation(project(":core:data"))
    implementation(project(":core:common"))
    implementation(project(":core:ui"))
    implementation(project(":core:database"))

    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit5.api)
    testRuntimeOnly(libs.junit5.engine)
    testImplementation(libs.mockk.core)
    testImplementation(libs.turbine)
    testImplementation(libs.kotlinx.coroutines.test)
}

tasks.withType<Test> {
    useJUnitPlatform()
}
