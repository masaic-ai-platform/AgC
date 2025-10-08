plugins {
    kotlin("jvm") version "2.1.0" apply false
    kotlin("plugin.spring") version "2.1.0" apply false
    id("org.springframework.boot") version "3.4.3" apply false
    id("io.spring.dependency-management") version "1.1.7" apply false
    kotlin("plugin.serialization") version "1.9.25" apply false
    id("org.jmailen.kotlinter") version "5.0.1" apply false
    id("java-library")
    id("maven-publish")
}

allprojects {
    group = "ai.masaic"
    version = "0.6.0-dev"

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
        }
    }
}
