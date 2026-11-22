// Economy simulator: plays whole runs of the core rules (core/, no Android SDK needed) with
// purchase, deck and reset policies, and reports the numbers the economy targets are checked against.
//   ./gradlew -p tools/economy-sim run --args="<hours per run> <seeds> <runs>"
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
        kotlin.srcDir("../../core/src/main/kotlin")
    }
}

application { mainClass.set("EconomySimKt") }

tasks.named<JavaExec>("run") {
    jvmArgs("-Xmx3g", "-Dfile.encoding=UTF-8", "-Dstdout.encoding=UTF-8")
}
