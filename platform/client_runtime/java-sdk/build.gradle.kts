plugins {
    java
    application
}

group = "ai.agc.sdk"
version = "1.0.0"

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.slf4j:slf4j-api:2.0.9")
    implementation("ch.qos.logback:logback-classic:1.4.14")
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

application {
    mainClass.set("ApplicationStart")
}

tasks.named<JavaExec>("run") {
    standardInput = System.`in`
}

