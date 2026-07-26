plugins {
    alias(libs.plugins.kotlin)
}

group = rootProject.property("project.group") as String
version = rootProject.property("project.version") as String

repositories.mavenCentral()

// The renderer module is deliberately free of Minecraft, LWJGL and Vulkan dependencies.
// It describes what to render; backends decide how. JOML is the only allowed dependency
// because matrices cross the API boundary.
dependencies {
    api(libs.joml)
}

kotlin {
    jvmToolchain(25)
    compilerOptions.freeCompilerArgs.add("-Xjvm-default=all")
}
