import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.w3c.dom.Document
import javax.xml.parsers.DocumentBuilderFactory
import net.fabricmc.loom.build.nesting.JarNester
import org.jetbrains.dokka.gradle.DokkaTaskPartial
import org.kohsuke.github.GHReleaseBuilder
import org.kohsuke.github.GitHub
import org.slf4j.LoggerFactory
import java.net.URL

buildscript {
    dependencies {
        classpath("org.kohsuke:github-api:latest.release")
    }
}

plugins{
    kotlin("jvm") version "latest.release"
    kotlin("plugin.serialization") version "latest.release"
    id("java")
    id("java-library")
    id("eclipse")
    id("maven-publish")
    id("checkstyle")
    id("com.diffplug.spotless") version "latest.release"
    id("moltenex-loom") version "1.9-SNAPSHOT" apply false
    id("me.modmuss50.remotesign") version "latest.release"
    id("com.gradleup.shadow") version "latest.release"
    id("org.jetbrains.dokka") version "latest.release"
}

base {
    archivesName = "moltenex-loader"
}

val ENV = System.getenv()

allprojects {
    apply(plugin = "org.jetbrains.dokka")
    apply(plugin = "kotlin")
    apply(plugin = "java-library")
    apply(plugin = "eclipse")
    apply(plugin = "checkstyle")
    apply(plugin = "com.diffplug.spotless")

    val constantsSource = rootProject.file("src/main/kotlin/com/moltenex/loader/impl/MoltenexLoaderImpl.kt").readText()
    version = (Regex("\\s+VERSION: String\\s*=\\s*\"(.*)\"").find(constantsSource)?.groups?.get(1)?.value ?: "") +
            if (System.getenv("GITHUB_ACTIONS") != null) "" else "+local"

    repositories {
        maven {
            name = "Fabric"
            url = uri("https://maven.fabricmc.net/")
        }
        mavenCentral {
            content {
                // Force ASM and ME to come from the fabric maven.
                // This ensures that the version has been mirrored for use by the launcher/installer.
                excludeGroupByRegex("org.ow2.asm")
                excludeGroupByRegex("io.github.llamalad7")
            }
        }
    }

    dependencies {
        implementation(kotlin("stdlib"))
        implementation("com.squareup.okio:okio:latest.release")
        implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:latest.release")
        implementation("org.jetbrains.kotlinx:kotlinx-serialization-core:latest.release")
    }

    // Checkstyle configuration
    checkstyle {
        configFile = project.rootProject.file("checkstyle.xml")
        toolVersion = "8.44"
    }

    // Spotless configuration
    spotless {
        kotlin {
            licenseHeaderFile(rootProject.file("HEADER"))
        }
        java {
            licenseHeaderFile(rootProject.file("HEADER"))
        }
    }
}

configurations {
    create("include") {
        isTransitive = false
    }

    implementation {
        extendsFrom(configurations["include"])
    }

    create("installer") {
        isTransitive = false
    }

    create("installerLaunchWrapper") {
        isTransitive = false
        extendsFrom(configurations["installer"])
    }

    create("development") {
        isTransitive = false
    }

    api {
        extendsFrom(configurations["installer"], configurations["development"])
    }
}

repositories {
    maven {
        name = "Mojang"
        url = uri("https://libraries.minecraft.net/")
        content {
            includeGroup("net.minecraft")
        }
    }
}

dependencies {
    // fabric-loader dependencies
    "installer"("org.ow2.asm:asm:${project.findProperty("asm_version")}")
    "installer"("org.ow2.asm:asm-analysis:${project.findProperty("asm_version")}")
    "installer"("org.ow2.asm:asm-commons:${project.findProperty("asm_version")}")
    "installer"("org.ow2.asm:asm-tree:${project.findProperty("asm_version")}")
    "installer"("org.ow2.asm:asm-util:${project.findProperty("asm_version")}")
    "installer"("net.fabricmc:sponge-mixin:${project.findProperty("mixin_version")}")
    "installerLaunchWrapper"("net.minecraft:launchwrapper:1.12")

    // impl dependencies
    "include"("org.ow2.sat4j:org.ow2.sat4j.core:2.3.6")
    "include"("org.ow2.sat4j:org.ow2.sat4j.pb:2.3.6")
    "include"("net.fabricmc:tiny-remapper:0.10.4")
    "include"("net.fabricmc:access-widener:2.1.0")
    "include"("net.fabricmc:mapping-io:0.5.0") {
        isTransitive = false
    }

    "development"("io.github.llamalad7:mixinextras-fabric:${project.findProperty("mixin_extras_version")}")

    testCompileOnly("org.jetbrains:annotations:23.0.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:latest.release")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-core:latest.release")

    // Unit testing for mod metadata
    testImplementation("org.junit.jupiter:junit-jupiter:5.9.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    testImplementation("org.mockito:mockito-core:5.10.0")
}

apply(from = rootProject.file("gradle/installer-json.gradle"))
apply(from = rootProject.file("gradle/launcher.gradle"))

tasks.processResources {
    inputs.property("version", project.version)

    filesMatching("moltenex.mod.json") {
        expand("version" to project.version)
    }
}

tasks.jar {
    isEnabled = false
    // Set the classifier to fix gradle task validation confusion.
    archiveClassifier.set("disabled")
}

tasks.test {
    useJUnitPlatform()
}

tasks.shadowJar {
    // Has stupid defaults, make our own
    isEnabled = false
}

tasks.register<Copy>("getSat4jAbout") {
    dependsOn(configurations["include"])
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE

    from({
        configurations["include"].files.map { file ->
            zipTree(file).matching {
                include("about.html")
            }
        }
    })

    rename("about.html", "net/fabricmc/loader/impl/lib/sat4j/about-sat4j.html")

    into(layout.buildDirectory.dir("sat4j"))
}

tasks.register<ShadowJar>("fatJar") {
    dependsOn(tasks.named("getSat4jAbout"))

    from(sourceSets.main.get().output)
    from(project(":minecraft").sourceSets.main.get().output)
    from(tasks.named<Copy>("getSat4jAbout").get().destinationDir)
    from("LICENSE.md") {
        rename { "${it}_${project.base.archivesName.get()}" }
    }

    manifest {
        attributes(
            "Main-Class" to "com.moltenex.loader.impl.launch.server.MoltenexServerLauncher",
            "Moltenex-Loom-Remap" to "false",
            "Automatic-Module-Name" to "com.moltenex.loader",
            "Multi-Release" to "true"
        )
    }

    archiveClassifier.set("fat")
    configurations = listOf(project.configurations.getByName("include"))

    relocate("org.sat4j", "net.fabricmc.loader.impl.lib.sat4j")
    relocate("net.fabricmc.accesswidener", "net.fabricmc.loader.impl.lib.accesswidener")
    relocate("net.fabricmc.tinyremapper", "net.fabricmc.loader.impl.lib.tinyremapper")
    relocate("net.fabricmc.mappingio", "net.fabricmc.loader.impl.lib.mappingio")

    exclude("about.html")
    exclude("sat4j.version")
    exclude("META-INF/maven/org.ow2.sat4j/*/**")
    exclude("META-INF/*.RSA")
    exclude("META-INF/*.SF")

    doLast {
        JarNester.nestJars(
            project.configurations["development"].files,
            archiveFile.get().asFile,
            LoggerFactory.getLogger("JiJ")
        )
    }

    outputs.upToDateWhen { false }
}

tasks.register<Zip>("finalJar") {
    dependsOn(tasks.named("fatJar")) // Ensure fatJar task is executed before finalJar

    // Use a file reference for the fatJar output once it's available
    from(zipTree(tasks.named<ShadowJar>("fatJar").get().archiveFile)) // Access the fatJar archive file

    destinationDirectory.set(file("build/libs"))
    archiveExtension.set("jar")
}


tasks.build {
    dependsOn(tasks.named("finalJar"))
}

tasks.register<Jar>("sourcesJar") {
    archiveClassifier.set("sources")
    from(sourceSets["main"].allSource)
    from(project(":minecraft").sourceSets["main"].allSource)
}

tasks.register<Jar>("testJar") {
    archiveClassifier.set("test")
    from(sourceSets["test"].output)
}

tasks.register("copyJson") {
    val inJson = file("src/main/resources/moltenex-installer.json")
    val inLwJson = file("src/main/resources/moltenex-installer.launchwrapper.json")

    val outJson = file("build/libs/${project.base.archivesName.get()}-${version}.json")
    val outLwJson = file("build/libs/${project.base.archivesName.get()}-${version}.launchwrapper.json")

    inputs.files(inJson, inLwJson)
    outputs.files(outJson, outLwJson)

    doLast {
        outJson.writeText(inJson.readText())
        outLwJson.writeText(inLwJson.readText())
    }
}

tasks.build {
    dependsOn(tasks.named("copyJson"))
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release =  17
}

tasks.withType<DokkaTaskPartial>().configureEach {
    outputDirectory.set(buildDir.resolve("dokka"))

    dokkaSourceSets {
        create("main") {
            // Include API package in the documentation generation
            includes.from("**/api/**")

            // Disable doclint options (similar to Javadoc)
            suppressInheritedMembers.set(true)

            // Add external documentation links
            externalDocumentationLink {
                url.set(URL("https://asm.ow2.io/javadoc/"))
            }
            externalDocumentationLink {
                url.set(URL("https://docs.oracle.com/javase/8/docs/api/"))
            }
            externalDocumentationLink {
                url.set(URL("https://logging.apache.org/log4j/2.x/javadoc/log4j-api/"))
            }
        }
    }
}

/*
tasks.register<Jar>("javadocJar") {
    dependsOn(tasks.named("dokkaHtmlMultiModule"))
    from(tasks.named<DokkaTask>("dokkaHtmlMultiModule").get().outputDirectory) // Use Dokka HTML output directory

    archiveClassifier.set("javadoc")
    destinationDirectory.set(file("build/libs"))
}

tasks.build {
    dependsOn(tasks.named("javadocJar"))
}*/

tasks.withType<GenerateModuleMetadata> {
    enabled = false
}

val signedJar = file("build/libs/moltenex-loader-${version}-signed.jar")
/*
remoteSign {
    requestUrl = ENV.SIGNING_SERVER
    pgpAuthKey = ENV.SIGNING_PGP_KEY
    jarAuthKey = ENV.SIGNING_JAR_KEY

    useDummyForTesting = ENV.SIGNING_SERVER == null

    sign(finalJar.archiveFile.get().asFile, signedJar, "final") {
        dependsOn(finalJar)
    }

    afterEvaluate {
        sign(publishing.publications["mavenJava"])
    }
}

publishing {
    publications {
        create("mavenJava", MavenPublication::class.java) {
            // Add all the jars that should be included when publishing to Maven
            artifact(signedJar) {
                builtBy(tasks.named("signFinal"))
                classifier = null
            }
            artifact(sourcesJar)
            artifact(javadocJar)
            artifact(file("src/main/resources/moltenex-installer.json")) {
                builtBy(tasks.named("copyJson"))
            }
            artifact(file("src/main/resources/moltenex-installer.launchwrapper.json")) {
                builtBy(tasks.named("copyJson"))
                classifier = "launchwrapper"
            }
        }
    }

    // Select the repositories you want to publish to
    repositories {
        if (ENV.MAVEN_URL != null) {
            maven {
                url = uri(ENV.MAVEN_URL)
                credentials {
                    username = ENV.MAVEN_USERNAME
                    password = ENV.MAVEN_PASSWORD
                }
            }
        }
    }
}*/

fun getBranch(): String {
    val env = System.getenv()
    return env["GITHUB_REF"]?.let {
        it.substring(it.lastIndexOf("/") + 1)
    } ?: "unknown"
}

tasks.register("github", DefaultTask::class) {
    dependsOn(tasks.named("publish"))
    onlyIf {
        System.getenv("GITHUB_TOKEN") != null
    }

    doLast {
        val github = GitHub.connectUsingOAuth(System.getenv("GITHUB_TOKEN"))
        val repository = github.getRepository(System.getenv("GITHUB_REPOSITORY"))

        val releaseBuilder = GHReleaseBuilder(repository, version.toString())
        releaseBuilder.name("Moltenex Loader $version")
        releaseBuilder.body(System.getenv("CHANGELOG") ?: "No changelog provided")
        releaseBuilder.commitish(getBranch())
        releaseBuilder.prerelease(false)

        releaseBuilder.create()
    }
}

tasks.register("checkVersion") {
    doFirst {
        val url = URL("https://maven.fabricmc.net/net/fabricmc/fabric-loader/maven-metadata.xml")
        val connection = url.openConnection()
        connection.connect()

        val documentBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder()
        val document: Document = documentBuilder.parse(connection.getInputStream())
        document.documentElement.normalize()

        val versions = document.getElementsByTagName("version")
        val versionList = mutableListOf<String>()
        for (i in 0 until versions.length) {
            versionList.add(versions.item(i).textContent)
        }

        if (versionList.contains(project.version.toString())) {
            throw RuntimeException("${project.version} has already been released!")
        }
    }
}

tasks.named("publish") {
    mustRunAfter(tasks.named("checkVersion"))
}

tasks.named("github") {
    mustRunAfter(tasks.named("checkVersion"))
}