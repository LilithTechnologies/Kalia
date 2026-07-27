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
    maven(url = "https://pkgs.dev.azure.com/djtheredstoner/DevAuth/_packaging/public/maven/v1") { name = "DevAuth" }
    mavenCentral()
}

configurations.configureEach {
    exclude(group = "org.lwjgl.lwjgl")
}

sourceSets {
    named("main") {
        java.srcDir("src/argentum/java")
        resources.srcDir("src/argentum/resources")
    }
}

dependencies {
    minecraft(libs.minecraft)
    mappings(variantOf(libs.legacy.yarn) { classifier("v2") })

    modImplementation(libs.legacy.lwjgl3)
    modImplementation(libs.fabric.loader)
    modImplementation(libs.devauth.fabric)
    modImplementation(libs.fabric.language.kotlin)

    ploceus.dependOsl("0.17.0")

    implementation(include(project(":vulkan-api"))!!)
    implementation(include(project(":renderer"))!!)
    implementation(include(project(":renderer:vulkan"))!!)
    implementation(include(project(":renderer:opengl"))!!)
    implementation(include(project(":terrain"))!!)

    testImplementation(kotlin("test"))
    testImplementation(project(":renderer:headless")) // the headless renderer is not shipped

    bundled(libs.joml)
    bundled(libs.lwjgl.asProvider())
    bundled(libs.lwjgl.vulkan)
    bundled(libs.lwjgl.vma)
    bundled(libs.lwjgl.shaderc)

    implementation(libs.lwjgl.opengl)
    implementation(libs.lwjgl.openal)

    lwjglDesktopNatives("lwjgl")
    lwjglDesktopNatives("lwjgl-vma")
    lwjglDesktopNatives("lwjgl-shaderc")

    lwjglDesktopNativesNoShade("lwjgl-opengl")
    lwjglDesktopNativesNoShade("lwjgl-openal")

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

fun DependencyHandlerScope.lwjglNativeNoShade(module: String, classifier: String) {
    val notation = "org.lwjgl:$module:${libs.versions.lwjgl.get()}:$classifier"
    add("runtimeOnly", notation)
    add("include", notation)
}

fun DependencyHandlerScope.lwjglDesktopNativesNoShade(module: String) {
    lwjglNativeNoShade(module, "natives-windows")
    lwjglNativeNoShade(module, "natives-linux")
    lwjglNativeNoShade(module, "natives-macos")
    lwjglNativeNoShade(module, "natives-macos-arm64")
}

kotlin {
    jvmToolchain(25)
}

loom {
    accessWidenerPath = file("src/main/resources/kalia.accesswidener")

    runConfigs {
        removeIf { it.name == "Minecraft Client" || it.name == "Minecraft Server" }

        create("ClientVulkan") {
            client()
            configName = "1.8.9 / Vulkan"
            ideConfigGenerated(true)
            runDir("run")
            jvmArguments.add("-Dkalia.backend=vulkan")
            jvmArguments.add("-Ddevauth.enabled=true")
        }
        create("ClientOpenGL") {
            client()
            configName = "1.8.9 / OpenGL"
            ideConfigGenerated(true)
            runDir("run")
            jvmArguments.add("-Dkalia.backend=opengl")
        }

    }
}

tasks.runClientRenderDoc {
    jvmArgs("-Ddevauth.enabled=true")
}