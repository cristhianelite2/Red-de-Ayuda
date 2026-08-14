import java.io.File

pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "RedDeAyuda"

include(":domain")
include(":simulator")

fun androidSdkPresent(): Boolean {
    val local = file("local.properties")
    if (local.exists()) {
        val sdkLine = local.readLines().firstOrNull { it.startsWith("sdk.dir=") }
        if (sdkLine != null) {
            val path = sdkLine.substringAfter("=").replace("\\\\", "\\").trim()
            if (file(path).exists()) return true
        }
    }
    val env = System.getenv("ANDROID_HOME") ?: System.getenv("ANDROID_SDK_ROOT")
    if (!env.isNullOrBlank() && file(env).exists()) return true
    val default = File(System.getProperty("user.home"), "AppData/Local/Android/Sdk")
    return default.exists()
}

if (androidSdkPresent()) {
    include(":data")
    include(":transport-ble")
    include(":transport-wifi")
    include(":platform")
    include(":app")
}
