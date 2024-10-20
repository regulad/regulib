// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.nexus.publish)
}

group = "xyz.regulad"
version = "1.1.0"

nexusPublishing {
    repositories {
        sonatype {
            nexusUrl.set(uri("https://s01.oss.sonatype.org/service/local/"))
            snapshotRepositoryUrl.set(uri("https://s01.oss.sonatype.org/content/repositories/snapshots/"))
            username = findProperty("ossrhUsername") as String? ?: System.getenv("OSSRH_USERNAME")
            password = findProperty("ossrhPassword") as String? ?: System.getenv("OSSRH_PASSWORD")
        }
    }
}
