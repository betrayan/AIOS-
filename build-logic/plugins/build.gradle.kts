plugins {
    `kotlin-dsl`
}

group = "com.buddy.aios.buildlogic"

// Version sync — must match libs.versions.toml
val agpVersion = "8.6.0"
val kotlinVersion = "2.0.21"

dependencies {
    compileOnly("com.android.tools.build:gradle:$agpVersion")
    compileOnly("org.jetbrains.kotlin:kotlin-gradle-plugin:$kotlinVersion")
}

gradlePlugin {
    plugins {
        register("androidApplication") {
            id = "buddy.android.application"
            implementationClass = "AndroidApplicationPlugin"
        }
        register("androidLibrary") {
            id = "buddy.android.library"
            implementationClass = "AndroidLibraryPlugin"
        }
        register("androidFeature") {
            id = "buddy.android.feature"
            implementationClass = "AndroidFeaturePlugin"
        }
        register("kotlinLibrary") {
            id = "buddy.kotlin.library"
            implementationClass = "KotlinLibraryPlugin"
        }
        register("androidHilt") {
            id = "buddy.android.hilt"
            implementationClass = "HiltPlugin"
        }
        register("androidRoom") {
            id = "buddy.android.room"
            implementationClass = "RoomPlugin"
        }
    }
}
