// Runs the game's own GameEngine.kt on the JVM (no Android SDK needed) to compare economy candidates.
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

sourceSets {
    main {
        kotlin.srcDir("../../app/src/main/java")
        kotlin.include("**/GameEngine.kt", "EconomySim.kt")
    }
}

application { mainClass.set("EconomySimKt") }

tasks.named<JavaExec>("run") {
    jvmArgs("-Xmx2g", "-Dfile.encoding=UTF-8", "-Dstdout.encoding=UTF-8")
}
