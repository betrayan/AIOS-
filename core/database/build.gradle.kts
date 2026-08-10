plugins {
    id("buddy.android.library")
    id("buddy.android.hilt")
    id("buddy.android.room")
}

android {
    namespace = "com.buddy.aios.core.database"
}

dependencies {
    implementation(project(":core:domain"))
    implementation(project(":core:security"))
    implementation(project(":core:common"))

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit5.api)
    testRuntimeOnly(libs.junit5.engine)
    testImplementation(libs.mockk.core)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
}

tasks.withType<Test> {
    useJUnitPlatform()
}
