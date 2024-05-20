import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent
import io.papermc.paperweight.Git

plugins {
    java
    `maven-publish`
    id("io.papermc.paperweight.patcher") version "1.7.1"
}

allprojects {
    apply(plugin = "java")
    apply(plugin = "maven-publish")

    java {
        toolchain {
            languageVersion = JavaLanguageVersion.of(21)
        }
    }
}

val paperMavenPublicUrl = "https://repo.papermc.io/repository/maven-public/"

subprojects {
    tasks.withType<JavaCompile>().configureEach {
        options.encoding = Charsets.UTF_8.name()
        options.release = 21
    }
    tasks.withType<Javadoc> {
        options.encoding = Charsets.UTF_8.name()
    }
    tasks.withType<ProcessResources> {
        filteringCharset = Charsets.UTF_8.name()
    }
    tasks.withType<Test> {
        testLogging {
            showStackTraces = true
            exceptionFormat = TestExceptionFormat.FULL
            events(TestLogEvent.STANDARD_OUT)
        }
    }

    repositories {
        mavenCentral()
        maven(paperMavenPublicUrl)
        maven("https://jitpack.io")
    }
}

repositories {
    mavenCentral()
    maven(paperMavenPublicUrl) {
        content {
            onlyForConfigurations(configurations.paperclip.name)
        }
    }
}

dependencies {
    remapper("net.fabricmc:tiny-remapper:0.10.1:fat")
    decompiler("org.vineflower:vineflower:1.10.1")
    paperclip("io.papermc:paperclip:3.0.3")
}

val paperDir = layout.projectDirectory.dir("work/Paper")
val initSubmodules by tasks.registering {
    outputs.upToDateWhen { false }
    doLast {
        Git(layout.projectDirectory)("submodule", "update", "--init").executeOut()
    }
}

paperweight {
    serverProject = project(":Purpur-server")

    remapRepo = paperMavenPublicUrl
    decompileRepo = paperMavenPublicUrl

    upstreams {
        register("paper") {
            upstreamDataTask {
                dependsOn(initSubmodules)
                projectDir = paperDir
            }

            patchTasks {
                register("api") {
                    upstreamDir = paperDir.dir("Paper-API")
                    patchDir = layout.projectDirectory.dir("patches/api")
                    outputDir = layout.projectDirectory.dir("Purpur-api")
                }
                register("server") {
                    upstreamDir = paperDir.dir("Paper-Server")
                    patchDir = layout.projectDirectory.dir("patches/server")
                    outputDir = layout.projectDirectory.dir("Purpur-server")
                    importMcDev = true
                }
                register("generatedApi") {
                    isBareDirectory = true
                    upstreamDir = paperDir.dir("paper-api-generator/generated")
                    patchDir = layout.projectDirectory.dir("patches/generatedApi")
                    outputDir = layout.projectDirectory.dir("paper-api-generator/generated")
                }
            }
        }
    }
}

tasks.generateDevelopmentBundle {
    apiCoordinates = "org.purpurmc.purpur:purpur-api"
    libraryRepositories = listOf(
        "https://repo.maven.apache.org/maven2/",
        paperMavenPublicUrl,
        "https://repo.purpurmc.org/snapshots",
    )
}

allprojects {
    publishing {
        repositories {
            maven("https://repo.purpurmc.org/snapshots") {
                name = "purpur"
                credentials(PasswordCredentials::class)
            }
        }
    }
}

publishing {
    publications.create<MavenPublication>("devBundle") {
        artifact(tasks.generateDevelopmentBundle) {
            artifactId = "dev-bundle"
        }
    }
}

tasks.register("printMinecraftVersion") {
    doLast {
        println(providers.gradleProperty("mcVersion").get().trim())
    }
}

tasks.register("printPurpurVersion") {
    doLast {
        println(project.version)
    }
}
