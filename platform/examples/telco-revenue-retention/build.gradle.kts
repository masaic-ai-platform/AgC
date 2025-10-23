plugins {
    java
    application
}

group = "ai.agc.usecase"
version = "1.0.0"

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.slf4j:slf4j-api:2.0.9")
    implementation("ch.qos.logback:logback-classic:1.4.14")
    implementation("com.openai:openai-java:2.2.0") {
        exclude(group = "io.grpc", module = "grpc-netty-shaded") // -18M
        exclude(group = "org.bouncycastle") // -17M if using JVM crypto
    }
    implementation("io.temporal:temporal-sdk:1.31.0")
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

application {
    mainClass.set("ApplicationStart")
}

tasks.named<JavaExec>("run") {
    dependsOn("build")
    standardInput = System.`in`
}

