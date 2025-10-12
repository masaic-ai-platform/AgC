plugins {
    kotlin("jvm")
    kotlin("plugin.spring")
    id("org.springframework.boot")
    id("io.spring.dependency-management")
    kotlin("plugin.serialization")
    // id("org.jmailen.kotlinter") // Temporarily disabled for hot reload feature
    application
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
    implementation(project(":open-responses-core"))
    implementation("io.temporal:temporal-sdk:1.31.0")
    implementation("io.grpc:grpc-netty-shaded:1.76.0")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("org.springframework.boot:spring-boot-starter")
    implementation("org.springframework.boot:spring-boot-starter-logging")
    implementation("io.methvin:directory-watcher:0.18.0")
    implementation("org.slf4j:slf4j-api")
    implementation("ch.qos.logback:logback-classic")
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict")
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21
    }
}

application {
    mainClass.set("ai.masaic.temporal.TemporalWorkerMain")
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

// Create a fat JAR with all dependencies
tasks.register<Jar>("fatJar") {
    group = "build"
    description = "Creates a fat JAR with all dependencies"
    archiveClassifier.set("all")
    
    from(sourceSets.main.get().output)
    dependsOn(configurations.runtimeClasspath)
    
    manifest {
        attributes["Main-Class"] = "ai.masaic.temporal.TemporalWorkerMain"
    }
    
    // Collect all service files and merge them
    val serviceFiles = mutableMapOf<String, MutableSet<String>>()
    
    configurations.runtimeClasspath.get().filter { it.name.endsWith("jar") }.forEach { file ->
        zipTree(file).matching {
            include("META-INF/services/*")
        }.forEach { serviceFile ->
            val relativePath = serviceFile.relativeTo(zipTree(file).asFileTree.files.first().parentFile.parentFile).path
            val lines = serviceFile.readLines().filter { it.isNotBlank() && !it.trim().startsWith("#") }
            serviceFiles.getOrPut(relativePath) { mutableSetOf() }.addAll(lines)
        }
    }
    
    from({
        configurations.runtimeClasspath.get().filter { it.name.endsWith("jar") }.map { zipTree(it) }
    }) {
        exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA")
        exclude("META-INF/services/*")
    }
    
    // Write merged service files
    doFirst {
        serviceFiles.forEach { (path, lines) ->
            val serviceFile = temporaryDir.resolve(path)
            serviceFile.parentFile.mkdirs()
            serviceFile.writeText(lines.joinToString("\n"))
        }
    }
    
    from(temporaryDir) {
        include("META-INF/services/*")
    }
    
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    isZip64 = true
}
