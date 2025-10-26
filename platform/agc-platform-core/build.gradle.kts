plugins {
    kotlin("jvm")
    kotlin("plugin.spring")
    id("org.springframework.boot")
    id("io.spring.dependency-management")
    kotlin("plugin.serialization")
    id("org.jmailen.kotlinter")
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
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
    compileOnly("org.springframework.boot:spring-boot-starter-data-mongodb-reactive")
    compileOnly("io.temporal:temporal-sdk:1.31.0")
    compileOnly("io.grpc:grpc-netty-shaded:1.76.0")
    compileOnly("org.redisson:redisson-spring-boot-starter:3.40.0")
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict")
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
    jvmArgs("--add-opens", "java.base/java.util=ALL-UNNAMED")
    testLogging {
        events("PASSED", "SKIPPED", "FAILED")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        showStandardStreams = true
    }
}

// Disable bootJar for library module - only the server module should create executable JARs
tasks.getByName<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    enabled = false
}

// Enable regular jar for library usage
tasks.getByName<Jar>("jar") {
    enabled = true
    // Ensure the main artifact has no classifier (avoid -plain)
    archiveClassifier.set("")
}
