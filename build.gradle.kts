import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("com.github.johnrengelman.shadow") version "7.0.0"
    kotlin("jvm") version "1.7.21"
    kotlin("kapt") version "1.7.21"
}

group = "cn.magicalsheep"
version = "0.1.0-SNAPSHOT"

repositories {
    maven {
        name = "papermc"
        url = uri("https://repo.papermc.io/repository/maven-public/")
    }
    mavenCentral()
}

dependencies {
    implementation("io.javalin:javalin:5.3.2")
    compileOnly("com.velocitypowered:velocity-api:3.1.1")
    kapt("com.velocitypowered:velocity-api:3.1.1")
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "17"
}