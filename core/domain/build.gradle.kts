// :core:domain — Pure Kotlin, zero Android dependencies.
// ARCHITECTURAL RULE: Do NOT add any Android dependency here.
plugins {
    id("buddy.kotlin.library")
}

dependencies {
    implementation("javax.inject:javax.inject:1")
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)

    // Testing
    testImplementation(libs.junit5.api)
    testRuntimeOnly(libs.junit5.engine)
    testImplementation(libs.mockk.core)
    testImplementation(libs.turbine)
    testImplementation(libs.kotlinx.coroutines.test)
}

tasks.withType<Test> {
    useJUnitPlatform()
}
