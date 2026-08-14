plugins {
    id("buddy.android.feature")
}

android {
    namespace = "com.buddy.aios.feature.home"
}

dependencies {
    implementation(project(":core:data"))
    implementation(project(":core:analytics"))
    implementation(project(":core:ai"))
    implementation(project(":core:common"))

    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.coil.compose)

    testImplementation(libs.junit5.api)
    testRuntimeOnly(libs.junit5.engine)
    testImplementation(libs.mockk.core)
    testImplementation(libs.turbine)
    testImplementation(libs.kotlinx.coroutines.test)
}

tasks.withType<Test> {
    useJUnitPlatform()
}
