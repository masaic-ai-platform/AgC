plugins {
    kotlin("jvm")                          // no version here – let Spring Boot manage it
    kotlin("plugin.spring")
    id("org.springframework.boot")         // no version – same
    id("io.spring.dependency-management")
    kotlin("plugin.serialization")
    id("org.jmailen.kotlinter")
}

version = "0.5.3-uc"

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
    implementation(project(":agc-platform-core"))
    implementation(project(":open-responses-rest"))
    implementation(project(":agc-platform-rest"))
    implementation("org.springframework.boot:spring-boot-starter-webflux")
    implementation("org.springframework.boot:spring-boot-starter-actuator")

    implementation("io.temporal:temporal-spring-boot-starter:1.31.0")
    // Add gRPC dependencies that Temporal needs
    api("io.grpc:grpc-netty-shaded:1.76.0")
//    implementation("io.grpc:grpc-protobuf:1.65.1")
//    implementation("io.grpc:grpc-stub:1.65.1")
//    implementation("io.grpc:grpc-netty:1.65.1")
}

// Removed broad gRPC exclusion as it's conflicting with Temporal SDK requirements
 configurations.all {
     exclude(group = "io.grpc", module = "grpc-netty")
 }

tasks.withType<org.springframework.boot.gradle.tasks.bundling.BootJar> {
    manifest {
        attributes["Implementation-Version"] = project.version
    }
}

springBoot {
    buildInfo()
}
