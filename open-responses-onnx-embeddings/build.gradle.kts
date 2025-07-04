plugins {
    kotlin("jvm")
    kotlin("plugin.spring")
    id("org.springframework.boot")
    id("io.spring.dependency-management")
    kotlin("plugin.serialization")
    id("org.jmailen.kotlinter")
}

dependencyManagement {
    imports {
        mavenBom("io.opentelemetry.instrumentation:opentelemetry-instrumentation-bom:2.14.0")
    }
}

dependencies {
    implementation(project(":open-responses-core"))
    implementation("org.springframework.boot:spring-boot-starter")
    implementation("dev.langchain4j:langchain4j-embeddings:1.0.0-beta2")
    implementation("dev.langchain4j:langchain4j-onnx-scoring:1.0.0-beta2")
    implementation("dev.langchain4j:langchain4j-embeddings-all-minilm-l6-v2:1.0.0-beta2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core")
    implementation("io.opentelemetry.instrumentation:opentelemetry-spring-boot-starter")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("com.ninja-squad:springmockk:4.0.2")
    testImplementation("io.mockk:mockk:1.13.17")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test")
    testImplementation("org.testcontainers:mongodb:1.19.1")
    testImplementation("org.testcontainers:junit-jupiter:1.19.1")
}

repositories {
    mavenCentral()
}

// Disable bootJar for library module
tasks.getByName<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    enabled = false
}

tasks.getByName<Jar>("jar") {
    enabled = true
}
