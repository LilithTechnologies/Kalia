plugins {
    kotlin("jvm")
}

group = rootProject.property("project.group") as String
version = rootProject.property("project.version") as String

repositories.mavenCentral()

dependencies {
    implementation(project(":renderer"))
    implementation(libs.joml)
    implementation(libs.fastutil)
}

kotlin {
    jvmToolchain(25)
}