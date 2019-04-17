@file: Suppress("SpellCheckingInspection")

import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version Kotlin.version
}

group = "com.hiczp"
version = "1.0.0"
description = "Retrofit inspired Http client base on CIO"

repositories {
    mavenCentral()
    maven("https://dl.bintray.com/kotlin/kotlinx/")
}

//kotlin
dependencies {
    implementation(kotlin("stdlib-jdk8"))
    implementation(kotlin("reflect"))
}
tasks.withType<KotlinCompile> {
    kotlinOptions {
        jvmTarget = Java.version
        freeCompilerArgs = listOf(
            "-Xuse-experimental=kotlin.Experimental",
            "-Xjvm-default=enable"
        )
    }
}

//cio
dependencies {
    // https://mvnrepository.com/artifact/io.ktor/ktor-client-cio
    implementation("io.ktor:ktor-client-cio:${Ktor.version}")
}

//test
dependencies {
    // https://mvnrepository.com/artifact/io.ktor/ktor-client-gson
    testImplementation("io.ktor:ktor-client-gson:${Ktor.version}")
    // https://mvnrepository.com/artifact/com.github.salomonbrys.kotson/kotson
    testImplementation("com.github.salomonbrys.kotson:kotson:2.5.0")
    // https://mvnrepository.com/artifact/io.ktor/ktor-client-logging-jvm
    testImplementation("io.ktor:ktor-client-logging-jvm:${Ktor.version}")
    // https://mvnrepository.com/artifact/org.slf4j/slf4j-simple
    testImplementation("org.slf4j:slf4j-simple:1.7.26")
    // https://mvnrepository.com/artifact/org.junit.jupiter/junit-jupiter
    testImplementation("org.junit.jupiter:junit-jupiter:5.4.2")
    // https://mvnrepository.com/artifact/io.ktor/ktor-client-mock-jvm
    testImplementation("io.ktor:ktor-client-mock-jvm:${Ktor.version}")
}
