plugins {
    alias(libs.plugins.kotlin)
    alias(libs.plugins.fabric.loom)
    alias(libs.plugins.ploceus)
}

group = property("project.group") as String
version = property("project.version") as String

ploceus {
    setIntermediaryGeneration(2)
}

repositories {
    maven(url = "https://maven.legacyfabric.net/") { name = "Legacy Fabric" }
    maven(url = "https://maven.axolotlclient.com/snapshots") { name = "Axolotl Client" }
    mavenCentral()
}

configurations.configureEach {
    exclude(group = "org.lwjgl.lwjgl")
}

sourceSets {
    named("main") {
        java.srcDir("src/lwjgl3/java")
        resources.srcDir("src/lwjgl3/resources")
        java.srcDir("src/sodium/java")
        resources.srcDir("src/sodium/resources")
    }
}

dependencies {
    minecraft(libs.minecraft)
    mappings(variantOf(libs.legacy.yarn) { classifier("v2") })

    modImplementation(libs.legacy.lwjgl3)
    modImplementation(libs.fabric.loader)

    ploceus.dependOsl("0.17.0")

    implementation(include(project(":vulkan-api"))!!)
    implementation(include(project(":renderer"))!!)
    implementation(include(project(":renderer:vulkan"))!!)
    implementation(include(project(":renderer:opengl"))!!)

    bundled(libs.joml)
    bundled(libs.lwjgl.asProvider())
    bundled(libs.lwjgl.opengl)
    bundled(libs.lwjgl.openal)
    bundled(libs.lwjgl.vulkan)
    bundled(libs.lwjgl.vma)
    bundled(libs.lwjgl.shaderc)

    lwjglDesktopNatives("lwjgl")
    lwjglDesktopNatives("lwjgl-opengl")
    lwjglDesktopNatives("lwjgl-openal")
    lwjglDesktopNatives("lwjgl-vma")
    lwjglDesktopNatives("lwjgl-shaderc")

    lwjglNative("lwjgl-vulkan", "natives-macos")
    lwjglNative("lwjgl-vulkan", "natives-macos-arm64")
}

fun DependencyHandlerScope.bundled(dependency: Provider<MinimalExternalModuleDependency>) {
    add("implementation", dependency)
    add("include", dependency)
}

fun DependencyHandlerScope.lwjglNative(module: String, classifier: String) {
    val notation = "org.lwjgl:$module:${libs.versions.lwjgl.get()}:$classifier"
    add("runtimeOnly", notation)
    add("include", notation)
}

fun DependencyHandlerScope.lwjglDesktopNatives(module: String) {
    lwjglNative(module, "natives-windows")
    lwjglNative(module, "natives-linux")
    lwjglNative(module, "natives-macos")
    lwjglNative(module, "natives-macos-arm64")
}

kotlin {
    jvmToolchain(25)
}

loom {
    accessWidenerPath = file("src/main/resources/kalia.accesswidener")

    runConfigs {
        removeIf { it.name == "Minecraft Client" || it.name == "Minecraft Server" }

        create("client-vulkan") {
            client()
            configName = "1.8.9 / Vulkan"
            ideConfigGenerated(true)
            runDir("run")
            jvmArguments.add("-Dkalia.backend=vulkan")
        }
        create("client-opengl") {
            client()
            configName = "1.8.9 / OpenGL"
            ideConfigGenerated(true)
            runDir("run")
            jvmArguments.add("-Dkalia.backend=opengl")
        }
    }
}