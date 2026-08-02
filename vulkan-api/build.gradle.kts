plugins {
    alias(libs.plugins.kotlin)
}

group = rootProject.property("project.group") as String
version = rootProject.property("project.version") as String

repositories.mavenCentral()

dependencies {
    implementation(libs.lwjgl)
    implementation(libs.lwjgl.sdl)
    implementation(libs.lwjgl.vulkan)
    implementation(libs.lwjgl.vma)
    implementation(libs.joml)
}

kotlin {
    jvmToolchain(21)
}