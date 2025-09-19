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
    implementation(project(":open-responses-core"))
    implementation("org.springframework.boot:spring-boot-starter-webflux")
//    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("com.openai:openai-java:2.2.0") {
        exclude(group = "io.grpc", module = "grpc-netty-shaded") // -18M
        exclude(group = "org.bouncycastle") // -17M if using JVM crypto
    }
//
    implementation("dev.langchain4j:langchain4j:1.0.0-beta2")
    implementation("io.github.microutils:kotlin-logging-jvm:3.0.5")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
//    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core")
    // ensure Kotlin stdlib is on the runtime classpath
//    implementation(kotlin("stdlib-jdk8"))
}

tasks.withType<org.springframework.boot.gradle.tasks.bundling.BootJar> {
    manifest {
        attributes["Implementation-Version"] = project.version
    }
}

springBoot {
    buildInfo()
}
