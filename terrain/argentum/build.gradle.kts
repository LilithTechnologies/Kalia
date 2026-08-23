plugins {
    alias(libs.plugins.kotlin)
    alias(libs.plugins.fabric.loom)
    alias(libs.plugins.ploceus)
}

group = rootProject.property("project.group") as String
version = rootProject.property("project.version") as String

ploceus {
    setIntermediaryGeneration(2)
}

repositories {
    maven(url = "https://maven.legacyfabric.net/") { name = "Legacy Fabric" }
}

dependencies {
    minecraft(libs.minecraft)
    mappings(variantOf(libs.legacy.yarn) { classifier("v2") })
    modImplementation(libs.fabric.loader)

    api(libs.joml)
    api(project(":terrain"))
    api(project(":renderer"))
}

kotlin {
    jvmToolchain(25)
    compilerOptions.freeCompilerArgs.add("-Xjvm-default=all")
}


tasks.runClient {
    enabled = false
}