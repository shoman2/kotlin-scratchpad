import com.google.cloud.tools.jib.image.ImageFormat
import org.jetbrains.dokka.DokkaConfiguration
import org.jetbrains.dokka.gradle.LinkMapping
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.net.URL

plugins {
    java
    application
    idea
    jacoco
    `maven-publish`
    signing
    distribution

    kotlin("jvm") version Versions.kotlin
    kotlin("kapt") version Versions.kotlin
    kotlin("plugin.noarg") version Versions.kotlin
    kotlin("plugin.allopen") version Versions.kotlin
    // kotlin("multiplatform") version Versions.kotlin
    // kotlin("serialization") version Versions.kotlin
    id("com.github.johnrengelman.shadow") version Versions.shadow
    id("com.github.ben-manes.versions") version Versions.benmanesVersions
    id("de.fayard.buildSrcVersions") version Versions.buildSrcVersions
    id("org.jlleitschuh.gradle.ktlint") version Versions.ktlint
    id("org.hidetake.swagger.generator") version Versions.swaggerGen
    id("com.gorylenko.gradle-git-properties") version Versions.gitProperties
    id("org.jetbrains.dokka") version Versions.dokka
    id("com.google.cloud.tools.jib") version Versions.googleJib
    id("net.researchgate.release") version Versions.gradleRelease
    id("io.spring.dependency-management") version Versions.springDepMgmt
    id("com.github.sherter.google-java-format") version Versions.googleJavaFormat
    id("org.sonarqube") version Versions.sonarqube
}

group = "io.sureshg"
version = "0.2.0"
description = "Kotlin scratchpad"

val gitUrl = "https://github.com/sureshg/kotlin-scratchpad"

application {
    mainClassName = "io.sureshg.MainKt"
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

ktlint {
    ignoreFailures.set(true)
}

jib {
    to {
        val tag = project.findProperty("tag") ?: project.version
        image = "sureshg/kotlin-demo:$tag"
        credHelper = "osxkeychain"
        auth {
            username = System.getenv("JIB_TO_USER")
            password = System.getenv("JIB_TO_PASSWORD")
        }
    }
    container {
        jvmFlags = listOf(
            "-server",
            "-Xms256m",
            "-XX:+UseG1GC",
            "-Djava.security.egd=file:/dev/./urandom"
        )
        mainClass = application.mainClassName
        args = listOf(project.description, project.version.toString())
        ports = listOf("8080-8090/tcp")
        useCurrentTimestamp = true
        format = ImageFormat.Docker
    }
    setExtraDirectory(File("src/main/resources"))
}

gitProperties {
    customProperties["kotlin"] = Versions.kotlin
}

release {
    revertOnFail = true
}

tasks {
    // Java main and test
    withType<JavaCompile> {
        options.apply {
            encoding = "UTF-8"
            isIncremental = true
            compilerArgs.addAll(listOf("-Xlint:all", "-parameters"))
        }
    }

    // Kotlin main and test
    withType<KotlinCompile> {
        kotlinOptions {
            verbose = true
            jvmTarget = "1.8"
            javaParameters = true
            freeCompilerArgs += listOf("-Xjsr305=strict", "-progressive")
        }
    }

    // JUnit5
    test {
        useJUnitPlatform()
        testLogging {
            events("passed", "skipped", "failed")
        }
        reports.html.isEnabled = true
    }

    // Distribution
    distTar {
        compression = Compression.GZIP
    }

    // Uber jar
    shadowJar {
        description = "Create a fat JAR of $archiveFileName and runtime dependencies."
        doLast {
            val fatJar = archiveFile.get().asFile
            println("FatJar: ${fatJar.path} (${fatJar.length().toDouble() / (1_000 * 1_000)} MB)")
        }
    }

    // Javadoc
    dokka {
        jdkVersion = 8
        outputFormat = "javadoc"
        outputDirectory = "$buildDir/javadoc"

        linkMapping(delegateClosureOf<LinkMapping> {
            dir = "src/main/kotlin"
            url = "$gitUrl/tree/master/src/main/kotlin"
            suffix = "#L"
        })

        externalDocumentationLink(delegateClosureOf<DokkaConfiguration.ExternalDocumentationLink.Builder> {
            url = URL("https://docs.oracle.com/javase/8/docs/api/")
        })
    }

    // Code Coverage
    jacocoTestReport {
        reports {
            xml.isEnabled = true
            html.isEnabled = false
            csv.isEnabled = false
        }
        dependsOn(":test")
    }

    // Google java format
    this.googleJavaFormat {
        dependsOn(":build")
    }

    // Release depends on publish.
    afterReleaseBuild {
        dependsOn(":publish")
    }

    // Generate pom
    withType<GenerateMavenPom> {
        destination = file("$buildDir/libs/${jar.get().baseName}.pom")
    }

    // Gradle Wrapper
    wrapper {
        gradleVersion = Versions.gradle
        distributionType = Wrapper.DistributionType.ALL
    }

    defaultTasks("clean", "tasks", "--all")
}

// Sources jar
val sourcesJar by tasks.registering(Jar::class) {
    archiveClassifier.set("sources")
    from(sourceSets.main.get().allSource)
}

// Javadoc jar
val javadocJar by tasks.registering(Jar::class) {
    archiveClassifier.set("javadoc")
    from(tasks.dokka)
}

// Tar distribution
distributions {
    getByName("main") {
        contents {
            from("${rootProject.projectDir}") {
                include("README.md", "LICENSE")
            }
            from("${rootProject.projectDir}/src/main/scripts") {
                fileMode = Integer.parseUnsignedInt("755", 8)
                into("bin")
            }
            from("${rootProject.projectDir}/pkg") {
                into("pkg")
            }
            into("lib") {
                from(tasks.jar)
            }
            into("lib") {
                from(configurations.runtimeClasspath)
            }
        }
    }
}

// SourceSets
// println(sourceSets.main.get().allSource.srcDirs)
// println(kotlin.sourceSets.test.get().kotlin.srcDirs)

publishing {
    repositories {
        maven {
            // change to point to your repo, e.g. http://my.org/repo
            url = uri("$buildDir/repo")
        }
    }
    publications {
        register<MavenPublication>("mavenJava") {
            from(components["java"])
            // artifact(tasks.jar.get())
            artifact(tasks.shadowJar.get())
            artifact(sourcesJar.get())
            artifact(javadocJar.get())
            artifact(tasks.distTar.get())

            // pom.addDependencies(project)
            pom {
                packaging = "jar"
                description.set("Gradle Kotlin DSL demo app")
                inceptionYear.set("2019")
                url.set(gitUrl)

                developers {
                    developer {
                        id.set("sureshg")
                        name.set("Suresh")
                        email.set("contact@sureshg.io")
                        url.set("https://sureshg.io")
                    }
                }

                licenses {
                    license {
                        name.set("The Apache Software License, Version 2.0")
                        url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
                        distribution.set("repo")
                    }
                }

                scm {
                    url.set(gitUrl)
                    connection.set("scm:git:git://github.com/sureshg/kotlin-scratchpad.git")
                    developerConnection.set("scm:git:ssh://git@github.com/sureshg/kotlin-scratchpad.git")
                }

                issueManagement {
                    system.set("github")
                    url.set("$gitUrl/issues")
                }
            }
        }
    }
}

repositories {
    maven(WmtPublic.url)
    mavenCentral()
}

dependencies {
    implementation(kotlin("stdlib-jdk8"))
    implementation(Deps.coroutinesCore)
    implementation(Deps.coroutinesJdk8)

    // HTTPS + JSON
    implementation(Deps.retrofit)
    implementation(Deps.okhttp)
    implementation(Deps.okhttpTls)
    implementation(Deps.moshi)
    implementation(Deps.moshiAdapters)
    implementation(Deps.retrofitCoroutinesAdapter)
    implementation(Deps.retrofitLogging)
    implementation(Deps.commonsCodec)
    implementation(Deps.retrofitConverterMoshi)
    kapt(Deps.moshiKotlinCodegen)

    // Logging
    implementation(Deps.slf4jApi)
    runtimeOnly(Deps.slf4jSimple)

    // Misc
    implementation(Deps.rsocketCore)
    implementation(Deps.rsocketNetty)
    implementation(Deps.jol)
    implementation(Deps.clikt)
    implementation(Deps.failsafe)
    compileOnly(Deps.jsr305)
    compileOnly(Deps.graalSdk)

    // JUnit5
    testImplementation(Deps.junitJupiterApi)
    testImplementation(Deps.junitJupiterParams)
    testRuntimeOnly(Deps.junitJupiterEngine)
    testImplementation(Deps.assertjCore)

    // Mock
    testImplementation(Deps.mockito)
    testImplementation(Deps.mockitoKotlin)
    testImplementation(Deps.okhttpMWS)
}
