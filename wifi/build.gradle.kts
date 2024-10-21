plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    id("maven-publish")
    id("signing")
}

publishing {
    publications {
        create<MavenPublication>("release") {
            groupId = rootProject.group.toString()
            artifactId = "regulib-wifi"
            version = rootProject.version.toString()

            afterEvaluate {
                from(components["release"])
            }

            pom {
                name.set("regulib-wifi")
                description.set("WiFi components for regulib")
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
        }
    }
}

signing {
    sign(publishing.publications["release"])
}

// https://developer.android.com/build/publish-library
android {
    namespace = "xyz.regulad.regulib.wifi"
    compileSdk = 34

    defaultConfig {
        minSdk = 21

        aarMetadata {
            minCompileSdk = 34
        }

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.1"
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
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.jetbrains.kotlinx.coroutines.test)
    androidTestImplementation(libs.jetbrains.kotlinx.coroutines.test)

    implementation(libs.commons.net)
    implementation(libs.okhttp)

    implementation(project(":common"))
}
