import com.github.jengelman.gradle.plugins.shadow.tasks.*
import org.gradle.api.JavaVersion.VERSION_11
import org.jetbrains.kotlin.gradle.dsl.*
import org.jetbrains.kotlin.gradle.dsl.Coroutines.ENABLE
import org.jetbrains.kotlin.gradle.tasks.*
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    val kotlinVer: String by System.getProperties()
    val shadowVer: String by System.getProperties()
    val versionsVer: String by System.getProperties()

    application
    idea
    jacoco
    `help-tasks`
    kotlin("jvm") version kotlinVer
    id("com.github.johnrengelman.shadow") version shadowVer
    id("com.github.ben-manes.versions") version versionsVer
}

val gradleVer: String by System.getProperties()
val ktCompatVer: String by System.getProperties()
val javaCompatVer: String by System.getProperties()
val coroutinesVer: String by System.getProperties()
val javaCompilerArgs = listOf("-Xlint:all", "-parameters")
val ktCompilerArgs = listOf("-Xjsr305=strict", "-Xprogressive")

group = "io.sureshg"
version = "0.1.0"
description = "Kotlin scratchpad"


application {
    mainClassName = "io.sureshg.MainKt"
}

kotlin {
    experimental.coroutines = ENABLE
}

java {
    sourceCompatibility = JavaVersion.toVersion(javaCompatVer)
    targetCompatibility = JavaVersion.toVersion(javaCompatVer)
}


tasks {
    withType<JavaCompile> {
        options.apply {
            encoding = "UTF-8"
            isIncremental = true
            compilerArgs.addAll(javaCompilerArgs)
        }
    }

    val compileKotlin by getting(KotlinCompile::class) {
        kotlinOptions {
            verbose = true
            jvmTarget = ktCompatVer
            freeCompilerArgs = ktCompilerArgs
        }
    }

    val compileTestKotlin by getting(KotlinCompile::class) {
        kotlinOptions {
            verbose = true
            jvmTarget = ktCompatVer
            freeCompilerArgs = ktCompilerArgs
        }
    }

    withType<ShadowJar> {
        description = "Create a fat JAR of $archiveName and runtime dependencies."
        doLast {
            println("FatJar: ${archivePath.path} (${archivePath.length().toDouble() / (1_000 * 1_000)} MB)")
        }
    }

    withType<JacocoReport> {
        this.reports {
            xml.isEnabled = true
            html.isEnabled = false
            csv.isEnabled = false
        }
        val jacocoTestReport by tasks
        jacocoTestReport.dependsOn("test")
    }

    getByName<Wrapper>("wrapper") {
        gradleVersion = gradleVer
        distributionType = Wrapper.DistributionType.ALL
    }

    defaultTasks("clean", "tasks", "--all")
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(kotlin("stdlib-jdk8"))
    implementation("org.jetbrains.kotlinx", "kotlinx-coroutines-core", coroutinesVer)
    implementation("org.jetbrains.kotlinx", "kotlinx-coroutines-jdk8", coroutinesVer)
    implementation("org.jetbrains.kotlinx", "kotlinx-coroutines-nio", coroutinesVer)
    implementation("com.squareup.retrofit2", "retrofit", "2.4.0")
    implementation("com.squareup.okhttp3", "okhttp", "3.11.0")
    implementation("com.squareup.okhttp3", "okhttp-tls", "3.11.0")
    implementation("com.squareup.moshi", "moshi", "1.6.0")
    kapt("com.squareup.moshi", "moshi-kotlin-codegen", "1.6.0")
    // implementation("org.jetbrains.kotlinx", "kotlinx-coroutines-reactor", coroutinesVer)
    testImplementation("com.squareup.okhttp3", "mockwebserver", "3.11.0")
}

