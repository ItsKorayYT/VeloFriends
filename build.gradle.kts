plugins {
    java
    id("com.gradleup.shadow") version "9.4.3"
}

group = "com.velofriends"
version = "1.0.1"
description = "VelocityFriends - friends, messaging, and social menus for Velocity networks"

val bundledSqliteNativePlatforms = providers.gradleProperty("sqliteNativePlatforms")
    .map { value ->
        value.split(",")
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .toSet()
    }
    .getOrElse(setOf("Linux/x86_64", "Windows/x86_64"))
val allSqliteNativePlatforms = setOf(
    "FreeBSD/aarch64",
    "FreeBSD/x86",
    "FreeBSD/x86_64",
    "Linux-Android/aarch64",
    "Linux-Android/arm",
    "Linux-Android/x86",
    "Linux-Android/x86_64",
    "Linux-Musl/aarch64",
    "Linux-Musl/x86",
    "Linux-Musl/x86_64",
    "Linux/aarch64",
    "Linux/arm",
    "Linux/armv6",
    "Linux/armv7",
    "Linux/ppc64",
    "Linux/riscv64",
    "Linux/x86",
    "Linux/x86_64",
    "Mac/aarch64",
    "Mac/x86_64",
    "Windows/aarch64",
    "Windows/armv7",
    "Windows/x86",
    "Windows/x86_64"
)

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}


dependencies {
    compileOnly("com.velocitypowered:velocity-api:3.5.0-SNAPSHOT")
    annotationProcessor("com.velocitypowered:velocity-api:3.5.0-SNAPSHOT")

    implementation("com.zaxxer:HikariCP:5.1.0")
    implementation("org.yaml:snakeyaml:2.2")
    implementation("org.xerial:sqlite-jdbc:3.46.1.0")
    implementation("com.mysql:mysql-connector-j:9.0.0")

    testImplementation(platform("org.junit:junit-bom:5.10.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
}

tasks {
    compileJava {
        options.encoding = "UTF-8"
        options.release.set(21)
    }

    processResources {
        filteringCharset = "UTF-8"
        filesMatching("velocityfriends.properties") {
            expand("version" to project.version)
        }
    }

    shadowJar {
        archiveClassifier.set("")
        mergeServiceFiles()
        relocate("org.yaml.snakeyaml", "com.velofriends.velocityfriends.libs.snakeyaml")
        for (platform in allSqliteNativePlatforms - bundledSqliteNativePlatforms) {
            exclude("org/sqlite/native/$platform/**")
        }
    }

    build {
        dependsOn(shadowJar)
    }

    test {
        useJUnitPlatform()
    }
}
