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
    api(project(":kyra-orm"))
    api(project(":kyra-json"))
    implementation("org.springframework.boot:spring-boot-autoconfigure:4.0.0")
    implementation("org.springframework:spring-jdbc:6.1.11")

    compileOnly("org.springframework:spring-web:7.0.1")
    compileOnly("org.springframework.boot:spring-boot-configuration-processor:4.0.0")
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor:4.0.0")
    compileOnly("org.projectlombok:lombok:1.18.44")
    annotationProcessor("org.projectlombok:lombok:1.18.44")
    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("com.h2database:h2:2.2.224")
    testImplementation("org.springframework.boot:spring-boot-starter-test:4.0.0")
    testImplementation("org.springframework.boot:spring-boot-autoconfigure:4.0.0")
    testImplementation("org.springframework:spring-web:7.0.1")
    testCompileOnly(project(":kyra-orm-processor"))
    testAnnotationProcessor(project(":kyra-orm-processor"))
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}
tasks.compileTestJava {
    options.compilerArgs.add("-Akyra.module=${project.name}")
}
