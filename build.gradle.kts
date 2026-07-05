plugins {
    java
    id("com.gradleup.shadow") version "8.3.6"
}

group = "com.velofriends"
version = "1.0.0"
description = "VelocityFriends - friends, messaging, and social menus for Velocity networks"

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
    }

    build {
        dependsOn(shadowJar)
    }

    test {
        useJUnitPlatform()
    }
}
