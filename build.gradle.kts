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
    maven(url = "https://maven.axolotlclient.com/releases") { name = "Axolotl Client" }
    maven(url = "https://pkgs.dev.azure.com/djtheredstoner/DevAuth/_packaging/public/maven/v1") { name = "DevAuth" }
    mavenCentral()
    exclusiveContent {
        forRepository { mavenCentral() }
        filter { includeGroup("org.lwjgl") }
    }
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

    modApi(libs.legacy.lwjgl3)
    modImplementation(libs.fabric.loader)
    modImplementation(libs.devauth.fabric)
    modImplementation(libs.fabric.language.kotlin)

    ploceus.dependOsl("0.17.0")

    api(include(project(":vulkan-api"))!!)
    api(include(project(":renderer"))!!)
    api(include(project(":renderer:vulkan"))!!)
    implementation(include(project(":terrain"))!!)

    testImplementation(kotlin("test"))
    testImplementation(project(":renderer:headless")) // the headless renderer is not shipped

    bundledApi(libs.joml)
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

fun DependencyHandlerScope.bundledApi(dependency: Provider<MinimalExternalModuleDependency>) {
    add("api", dependency)
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

allprojects {
    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
        compilerOptions.freeCompilerArgs.addAll("-Xno-param-assertions", "-Xno-call-assertions")
    }
}

loom {
    accessWidenerPath = file("src/main/resources/kalia.accesswidener")

    runs.named("client") {
        ideConfigGenerated(true)
        runDir("run")

        jvmArguments.add("-Xms4G")
        jvmArguments.add("-Xmx8G")

        jvmArguments.add("-Dkalia.backend=vulkan")
        jvmArguments.add("-Ddevauth.enabled=true")

        jvmArguments.add("-XX:+UseCompactObjectHeaders")
        jvmArguments.add("-XX:+UseZGC")
        jvmArguments.add("-XX:+AlwaysPreTouch")
        jvmArguments.add("-XX:+DisableExplicitGC")
    }
}

allprojects {
    apply(plugin = "maven-publish")
    apply(plugin = "java")

    fun setting(property: String, environment: String): String? =
        (providers.gradleProperty(property).orNull ?: providers.environmentVariable(environment).orNull)
            ?.takeIf { it.isNotBlank() }

    val cloverUsername = setting("cloverUsername", "CLOVER_MAVEN_USERNAME")
    val cloverPassword = setting("cloverPassword", "CLOVER_MAVEN_PASSWORD")
    val cloverUrl = setting("cloverUrl", "CLOVER_MAVEN_URL")

    extensions.configure<PublishingExtension> {
        publications {
            create<MavenPublication>("maven") {
                from(components["java"])

                pom {
                    url = "https://maven.cloverclient.com"
                }
            }
        }

        repositories {
            maven {
                name = "Clover"

                url = uri(
                    cloverUrl ?: if (version.toString().endsWith("SNAPSHOT")) {
                        "https://maven.cloverclient.com/snapshots"
                    } else {
                        "https://maven.cloverclient.com/releases"
                    },
                )

                credentials {
                    username = cloverUsername
                    password = cloverPassword
                }
            }
        }
    }
}

tasks.runClientRenderDoc {
    jvmArgs("-Ddevauth.enabled=true")
}