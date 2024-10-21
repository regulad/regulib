# ReguLib

[![wakatime](https://wakatime.com/badge/user/85016631-21af-4af7-a0fc-5e8c648e855b/project/aa59aad0-81d4-4764-b2a5-0c86f4f54e31.svg)](https://wakatime.com/badge/user/85016631-21af-4af7-a0fc-5e8c648e855b/project/aa59aad0-81d4-4764-b2a5-0c86f4f54e31)

![Maven Central Version](https://img.shields.io/maven-central/v/xyz.regulad/regulib-common)

ReguLib is an Android library used by my Android projects.

It provides commonly-reused utilities, including:

* Kotlin cold Flow caching
* Reliable worry-free Bluetooth LE & WiFi Direct connections
* Subnet scanning
* Flow transformation utilities
* Composable Flow utilities
* QR generation components
* Version-agnostic support for Java 1.8 Collection & Map features under Kotlin
* And more!

For more info, check out the JavaDoc at one of the links below:

* https://regulad.github.io/regulib/common
* https://regulad.github.io/regulib/ble
* https://regulad.github.io/regulib/wifi
* https://regulad.github.io/regulib/compose

## Installation

ReguLib is available on Maven Central.

Get the version from the badge above, and add the following to your `build.gradle.kts` per-module file:

```kotlin
dependencies {
    implementation("xyz.regulad:regulib-common:$version")
    implementation("xyz.regulad:regulib-ble:$version")
    implementation("xyz.regulad:regulib-wifi:$version")
    implementation("xyz.regulad:regulib-compose:$version")
}
```

You may run into version conflicts if you use other libraries that depend on different versions of Kotlin or other
libraries. Check the `gradle/libs.versions.toml` for the versions of dependencies ReguLib relies on.

Due to the size of the libraries, it is highly recommended to set up ProGuard rules to remove unused code.

## Publishing

To publish a new version of ReguLib, you need to have the following environment variables set:

* `OSSRH_USERNAME`: Your Sonatype username
* `OSSRH_PASSWORD`: Your Sonatype password

In addition, you also have to have Gradle signing set up. You can do this by creating a `gradle.properties` file in your
home directory with the following contents:

```properties
signing.keyId=YOUR_KEY_ID
signing.password=YOUR_KEY_PASSWORD
signing.secretKeyRingFile=/path/to/your/.gnupg/secring.gpg
```

Then, you can run the following command to publish:

```shell
./gradlew publish closeAndReleaseStagingRepositories
```
