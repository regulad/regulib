plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    id("maven-publish")
    id("signing")
}

publishing {
    publications {
        create<MavenPublication>("release") {
            groupId = rootProject.group.toString()
            artifactId = "regulib-common"
            version = rootProject.version.toString()

            afterEvaluate {
                from(components["release"])
            }

            pom {
                name.set("regulib-common")
                description.set("Common components for regulib")
                url.set("https://github.com/regulad/regulib")

                licenses {
                    license {
                        name.set("GNU Lesser General Public License v3.0")
                        url.set("https://www.gnu.org/licenses/lgpl-3.0.txt")
                        distribution.set("repo")
                    }
                }

                developers {
                    developer {
                        id.set("regulad")
                        name.set("Parker Wahle")
                        email.set("parkeredwardwahle2017@gmail.com")
                    }
                }

                scm {
                    connection.set("scm:git:git://github.com/regulad/regulib.git")
                    developerConnection.set("scm:git:ssh://github.com:regulad/regulib.git")
                    url.set("https://github.com/regulad/regulib")
                }
            }

            repositories {
                maven {
                    name = "OSSRH"
                    url = uri("https://s01.oss.sonatype.org/service/local/staging/deploy/maven2/")
                    credentials {
                        username = findProperty("ossrhUsername") as String? ?: System.getenv("OSSRH_USERNAME")
                        password = findProperty("ossrhPassword") as String? ?: System.getenv("OSSRH_PASSWORD")
                    }
                }
            }
        }
    }
}

signing {
    sign(publishing.publications["release"])
}

// https://developer.android.com/build/publish-library
android {
    namespace = "xyz.regulad.regulib"
    compileSdk = 34

    defaultConfig {
        minSdk = 21

        aarMetadata {
            minCompileSdk = 34
        }

        testInstrumentationRunner = "android.support.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    testFixtures {
        enable = true
    }

    publishing {
        singleVariant("release") {
            withSourcesJar()
            withJavadocJar()
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
}

dependencies {
    implementation(libs.appcompat.v7)
    testImplementation(libs.junit)
    androidTestImplementation(libs.runner)
    androidTestImplementation(libs.espresso.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)
}
