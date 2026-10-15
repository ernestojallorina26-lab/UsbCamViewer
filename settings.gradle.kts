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
        maven { url = uri("https://jitpack.io") }
        // Fallback source for older libraries the USB camera library depends on.
        @Suppress("DEPRECATION")
        jcenter()
    }
}

rootProject.name = "UsbCamViewer"
include(":app")
