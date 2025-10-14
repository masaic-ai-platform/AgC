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
    api(project(":agc-platform-core")) {
        exclude(group = "io.temporal:temporal-sdk", module = "temporal-sdk")
    }
    api(project(":open-responses-rest"))
    api(project(":agc-platform-rest"))
    implementation("io.temporal:temporal-spring-boot-starter:1.31.0")
    implementation("org.springframework.boot:spring-boot-starter-webflux")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("com.google.api-client:google-api-client:2.7.0")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
}

tasks.withType<org.springframework.boot.gradle.tasks.bundling.BootJar> {
    manifest {
        attributes["Implementation-Version"] = project.version
    }
}

springBoot {
    buildInfo()
}
