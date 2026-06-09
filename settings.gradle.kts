pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
    plugins {
        id("com.vanniktech.maven.publish") version "0.36.0"
        id("io.quarkus.extension") version providers.gradleProperty("quarkusVersion").get()
    }
}

rootProject.name = "kyra"

include("kyra-core")
include("kyra-processor-core")
include("kyra-processor")
include("kyra-orm")
include("kyra-orm-processor")
include("kyra-quarkus")
include("kyra-quarkus-deployment")
include("kyra-spring-boot")
include("kyra-excel")
include("kyra-json")
include("simple")
