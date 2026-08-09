plugins {
    alias(libs.plugins.kotlin)
}

group = rootProject.property("project.group") as String
version = rootProject.property("project.version") as String

repositories.mavenCentral()

dependencies {
    api(libs.joml)

    // We need this for MemoryUtil
    implementation(libs.lwjgl)
}

kotlin {
    jvmToolchain(21)
    compilerOptions.freeCompilerArgs.add("-Xjvm-default=all")
}
