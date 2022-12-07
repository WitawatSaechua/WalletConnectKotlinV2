import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("java-library")
    kotlin("jvm")
    id("com.google.devtools.ksp") version kspVersion
    id("publish-module-java")
}

project.apply {
    extra[KEY_PUBLISH_ARTIFACT_ID] = "foundation"
    extra[KEY_PUBLISH_VERSION] = "1.1.0"
    extra[KEY_SDK_NAME] = "Foundation"
}

java {
    sourceCompatibility = jvmVersion
    targetCompatibility = jvmVersion
}

tasks.withType<KotlinCompile>() {
    kotlinOptions {
        jvmTarget = jvmVersion.toString()
    }
}

tasks.withType<Test>() {
    systemProperty("SDK_VERSION", requireNotNull(project.extra.get(KEY_PUBLISH_VERSION)))
    systemProperty("TEST_RELAY_URL", System.getenv("TEST_RELAY_URL"))
    systemProperty("TEST_PROJECT_ID", System.getenv("TEST_PROJECT_ID"))
}

dependencies {
    scarlet()
    okhttp()
    koinJvm()
    moshi()
    moshiKsp()
    bouncyCastle()
    multibaseJava()
    restEasyJava()

    jUnit5()
    mockk()
}
