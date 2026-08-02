pluginManagement {
    repositories {
        maven(url = "https://maven.fabricmc.net") { name = "Fabric" }
        maven(url = "https://maven.ornithemc.net/releases") { name = "Ornithe Releases" }
        maven(url = "https://maven.ornithemc.net/snapshots") { name = "Ornithe Snapshots" }

        mavenLocal()
        gradlePluginPortal()
    }
}

include(":vulkan-api")
include(":renderer")
include(":renderer:vulkan")

rootProject.name = "Kalia"

include("renderer:headless")
include("terrain")