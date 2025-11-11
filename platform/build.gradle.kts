plugins {
    kotlin("jvm") version "2.1.0" apply false
    kotlin("plugin.spring") version "2.1.0" apply false
    id("org.springframework.boot") version "3.5.7" apply false
    id("io.spring.dependency-management") version "1.1.7" apply false
    kotlin("plugin.serialization") version "1.9.25" apply false
    id("org.jmailen.kotlinter") version "5.0.1" apply false
    id("java-library")
    id("maven-publish")
}

// Determine the version - allow override from command line
val projectVersion = (findProperty("buildVersion") as String?)
    ?.takeIf { it.isNotBlank() } ?: "0.8.0"

allprojects {
    group = "ai.masaic.agc"
    version = projectVersion

    repositories {
        mavenCentral()
    }
}

java {
    withSourcesJar()
}

// Ensure all subprojects publish to local Maven
subprojects {
    // Apply maven-publish to every subproject
    apply(plugin = "maven-publish")

    // Add sources jar for Java/Kotlin projects
    plugins.withId("java") {
        extensions.configure<JavaPluginExtension> {
            withSourcesJar()
        }
    }

    // Configure a publication per subproject, choosing the right artifact
    afterEvaluate {
        publishing {
            publications {
                create<MavenPublication>("maven") {
                    groupId = project.group.toString()
                    artifactId = project.name
                    version = project.version.toString()

                    val javaComponent = components.findByName("java")
                    val jarEnabled = tasks.findByName("jar")?.let { (it as org.gradle.api.tasks.bundling.Jar).enabled } ?: false
                    val bootJarTask = tasks.findByName("bootJar")

                    if (javaComponent != null && jarEnabled) {
                        from(javaComponent)
                    } else if (bootJarTask != null) {
                        artifact(bootJarTask)
                    } else if (javaComponent != null) {
                        // Fallback to java component even if jar task state is unknown
                        from(javaComponent)
                    }
                }
            }
            
            repositories {
                maven {
                    name = "GitHubPackages"
                    url = uri("https://maven.pkg.github.com/masaic-ai-platform/AgC")
                    credentials {
                        username = System.getenv("GITHUB_ACTOR") ?: findProperty("gpr.user") as String?
                        password = System.getenv("GITHUB_TOKEN") ?: findProperty("gpr.key") as String?
                    }
                }
            }
        }
    }
}
