plugins {
    kotlin("jvm")                          // no version here – let Spring Boot manage it
    kotlin("plugin.spring")
    id("org.springframework.boot")         // no version – same
    id("io.spring.dependency-management")
    kotlin("plugin.serialization")
    id("org.jmailen.kotlinter")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

dependencyManagement {
    imports {
        mavenBom("io.opentelemetry.instrumentation:opentelemetry-instrumentation-bom:2.14.0")
    }
}

repositories {
    mavenCentral()
}

dependencies {
    api(project(":open-responses-core"))
    api(project(":open-responses-rest"))
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    runtimeOnly("org.springframework.boot:spring-boot-starter-data-mongodb-reactive")

    // ensure Kotlin stdlib is on the runtime classpath
    implementation(kotlin("stdlib-jdk8"))
    testImplementation("org.springframework.boot:spring-boot-starter-test")
}

tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    // always set your app’s main class
    mainClass.set("ai.masaic.OpenResponsesApplicationKt")
    // core-only remains openresponses-<version>.jar
    archiveBaseName.set("openresponses")
    archiveClassifier.set("")
}

tasks.withType<org.springframework.boot.gradle.tasks.bundling.BootJar> {
    manifest {
        attributes["Implementation-Version"] = project.version
    }
}

springBoot {
    buildInfo()
}
