plugins {
    id("java")
    id("application")
}

group = "ai.masaic"
version = "0.5.1"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    implementation("com.openai:openai-java:2.2.0") {
        exclude(group = "io.grpc", module = "grpc-netty-shaded") // -18M
        exclude(group = "org.bouncycastle") // -17M if using JVM crypto
    }
    implementation("com.fasterxml.jackson.core:jackson-databind:2.15.2")
}

application {
    mainClass.set("ai.masaic.examples.AgCLoopWithMCPExample")
}

tasks.test {
    useJUnitPlatform()
}
