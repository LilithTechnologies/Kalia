plugins {
    alias(libs.plugins.kotlin)
}

group = rootProject.property("project.group") as String
version = rootProject.property("project.version") as String

repositories.mavenCentral()

dependencies {
    api(project(":renderer"))
    implementation(project(":vulkan-api"))

    implementation(libs.lwjgl)
    implementation(libs.lwjgl.sdl)
    implementation(libs.lwjgl.vulkan)
    implementation(libs.lwjgl.vma)

    compileOnly(libs.lwjgl.shaderc)
    runtimeOnly(libs.lwjgl.shaderc)

    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(25)
}
