plugins {
    java
}

group = "dev.shinobi"
version = "0.12.0-stage3d"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    // Matches a Paper 1.21.11 server (Java 21).
    compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.release.set(21)
}

tasks.processResources {
    filteringCharset = "UTF-8"
    filesMatching("plugin.yml") {
        expand("version" to project.version)
    }
}

// Plain-Java tests for the pure charge and bloodline rules (no Bukkit, no JUnit). Part of `gradle build` via `check`.
val logicTests by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "Runs the pure-logic tests (charging, bloodlines, migration, dodge, cast-route guard)"
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("dev.shinobi.AllLogicTests")
}
tasks.named("check") { dependsOn(logicTests) }
