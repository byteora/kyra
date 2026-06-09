import org.gradle.api.tasks.compile.JavaCompile

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
    implementation(project(":kyra-quarkus"))
    implementation("io.quarkus:quarkus-core-deployment:$quarkusVersion")
    implementation("io.quarkus:quarkus-arc-deployment:$quarkusVersion")

    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation(platform("io.quarkus:quarkus-bom:$quarkusVersion"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("io.quarkus:quarkus-junit5:$quarkusVersion")
    testImplementation("io.quarkus:quarkus-junit5-internal:$quarkusVersion")
    testImplementation("io.quarkus:quarkus-agroal")
    testImplementation("io.quarkus:quarkus-jdbc-h2")
    testImplementation("com.h2database:h2:2.2.224")
    testCompileOnly(project(":kyra-orm-processor"))
    testAnnotationProcessor(project(":kyra-orm-processor"))
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}
tasks.javadoc {
    enabled = false
}
tasks.withType<JavaCompile>().configureEach {
    if (name == "compileTestJava") {
        options.compilerArgs.add("-Akyra.mapper=${project.projectDir}/src/test/resources/mapper")
    }
}
