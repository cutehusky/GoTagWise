plugins {
    java
    id("org.jetbrains.intellij.platform") version "2.19.0"
}

group = "dev.structtags"
version = "0.1.0"

repositories {
    mavenCentral()
    intellijPlatform { defaultRepositories() }
}

dependencies {
    intellijPlatform {
        val localIde = providers.gradleProperty("localIdePath").orNull
        if (localIde != null) local(localIde) else goland("2026.2.1.1")
        bundledPlugin("org.jetbrains.plugins.go")
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}
tasks.withType<JavaCompile>().configureEach { options.release = 21 }
intellijPlatform {
    pluginConfiguration {
        ideaVersion { sinceBuild = "262" }
    }
}
val coreTest by tasks.registering(JavaExec::class) {
    dependsOn(tasks.testClasses)
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass = "dev.structtags.core.CoreTest"
    workingDir = projectDir
}
tasks.check { dependsOn(coreTest) }
// The dependency-free suite is executed by coreTest; IDE integration uses test-ide.ps1.
tasks.test { enabled = false }
