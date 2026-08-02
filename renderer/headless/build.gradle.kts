plugins {
    alias(libs.plugins.kotlin)
}

group = rootProject.property("project.group") as String
version = rootProject.property("project.version") as String

repositories.mavenCentral()

dependencies {
    api(project(":renderer"))
}

kotlin {
    jvmToolchain(21)
}
