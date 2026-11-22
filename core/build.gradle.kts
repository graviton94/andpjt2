// Core rules v2: pure Kotlin, no Android. The app will depend on this module; tools run it on the JVM.
plugins {
    kotlin("jvm") version "2.2.21"
    application
}

kotlin {
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
}
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    testImplementation(kotlin("test"))
}

tasks.test { useJUnitPlatform() }

// ./gradlew -p core run  → prints the fixed-seed examples used in docs/core-rules-v2.md
application { mainClass.set("com.graviton94.notificationcleaner.core.ExamplesKt") }
tasks.named<JavaExec>("run") { jvmArgs("-Dfile.encoding=UTF-8", "-Dstdout.encoding=UTF-8") }
