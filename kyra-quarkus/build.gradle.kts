plugins {
    id("java-library")
}

val quarkusVersion: String by project

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
    withSourcesJar()
}

dependencies {
    api(project(":kyra-orm"))
    api(project(":kyra-json"))

    implementation(platform("io.quarkus:quarkus-bom:$quarkusVersion"))
    implementation("io.quarkus:quarkus-arc")
    implementation("io.quarkus:quarkus-rest")

    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testCompileOnly(project(":kyra-processor"))
    testAnnotationProcessor(project(":kyra-processor"))
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}

tasks.processResources {
    inputs.property("projectGroup", project.group.toString())
    inputs.property("projectVersion", project.version.toString())
    filesMatching("META-INF/quarkus-extension.properties") {
        expand(
            "projectGroup" to project.group,
            "projectVersion" to project.version
        )
    }
}
