pluginManagement {
    repositories {
        mavenLocal()
        maven {
            url = uri("https://maven.fabricmc.net")
            name = "FabricMC"
        }
        gradlePluginPortal()
        mavenCentral()
    }
}


plugins {
    // Plugin for resolving the correct Java toolchain, ensuring Java 17+ is used.
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}
/*
// Verifying the Java version before proceeding with the build.
if (!JavaVersion.current().isCompatibleWith(JavaVersion.VERSION_17)) {
    throw UnsupportedOperationException("Moltenex Loader requires Java 17+ to build.")
}*/

// Defining the subprojects included in the build.
include("minecraft")  // The main project related to Minecraft.
include("junit")      // Subproject for unit tests.
//include("minecraft:minecraft-test")  // Nested project for Minecraft-specific tests.
