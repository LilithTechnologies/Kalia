plugins {
    alias(libs.plugins.kotlin)
}

group = rootProject.property("project.group") as String
version = rootProject.property("project.version") as String

repositories.mavenCentral()

dependencies {
    api(project(":renderer"))

    implementation(libs.lwjgl)
    implementation(libs.lwjgl.sdl)
    implementation(libs.lwjgl.opengl)
}

kotlin {
    jvmToolchain(21)
}
