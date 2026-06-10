import org.gradle.api.tasks.JavaExec

plugins {
    id("java-library")
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
    withSourcesJar()
}

dependencies {
    api(project(":kyra-core"))
    api("tools.jackson.core:jackson-core:3.1.3")
    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("tools.jackson.core:jackson-databind:3.1.3")
    testImplementation("org.openjdk.jmh:jmh-core:1.37")
    testAnnotationProcessor(project(":kyra-processor"))
    testAnnotationProcessor("org.openjdk.jmh:jmh-generator-annprocess:1.37")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}
tasks.compileTestJava {
    options.compilerArgs.add("-Akyra.module=${project.name}")
}

tasks.register<JavaExec>("jmh") {
    group = "verification"
    description = "Run JMH benchmarks comparing kyra-json and Jackson"
    dependsOn(tasks.testClasses)
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("org.openjdk.jmh.Main")
    if (project.hasProperty("jmhArgs")) {
        @Suppress("UNCHECKED_CAST")
        args((project.property("jmhArgs") as String).split(" ").filter { it.isNotBlank() })
    }
}