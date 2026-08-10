import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

/**
 * Convention plugin for Room database modules.
 * Apply with: id("buddy.android.room")
 */
class RoomPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.google.devtools.ksp")
            dependencies {
                add("implementation", "androidx.room:room-runtime:2.6.1")
                add("implementation", "androidx.room:room-ktx:2.6.1")
                add("ksp", "androidx.room:room-compiler:2.6.1")
                add("testImplementation", "androidx.room:room-testing:2.6.1")
            }
        }
    }
}
