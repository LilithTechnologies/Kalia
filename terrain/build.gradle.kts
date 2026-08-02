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
    implementation(libs.bundles.asm)
    implementation(libs.gson)
    implementation(libs.annotations)
    implementation(libs.log4j.core)

    implementation(libs.lombok)
    annotationProcessor(libs.lombok)

    testImplementation(kotlin("test"))
    testImplementation(project(":renderer:headless"))
}

kotlin {
    jvmToolchain(21)
}