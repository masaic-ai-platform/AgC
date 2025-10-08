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
    implementation("com.knuddels:jtokkit:1.1.0")
    implementation("org.springframework.boot:spring-boot-starter-webflux")
//    testImplementation("io.mockk:mockk:1.13.17")
//    testImplementation("com.ninja-squad:springmockk:4.0.2")
//    testImplementation("org.springframework.boot:spring-boot-starter-test")
//    implementation("com.openai:openai-java:2.2.0") {
//        exclude(group = "io.grpc", module = "grpc-netty-shaded") // -18M
//        exclude(group = "org.bouncycastle") // -17M if using JVM crypto
//    }

//    implementation("dev.langchain4j:langchain4j:1.0.0-beta2")
//    implementation("io.github.microutils:kotlin-logging-jvm:3.0.5")
//    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
//    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core")
    // ensure Kotlin stdlib is on the runtime classpath
//    implementation(kotlin("stdlib-jdk8"))
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
