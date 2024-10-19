# ReguLib

[![wakatime](https://wakatime.com/badge/user/85016631-21af-4af7-a0fc-5e8c648e855b/project/aa59aad0-81d4-4764-b2a5-0c86f4f54e31.svg)](https://wakatime.com/badge/user/85016631-21af-4af7-a0fc-5e8c648e855b/project/aa59aad0-81d4-4764-b2a5-0c86f4f54e31)

![Maven Central Version](https://img.shields.io/maven-central/v/xyz.regulad/regulib-common)

ReguLib is an Android library used by my Android projects.

It provides commonly-reused utilities, including:

* Kotlin cold Flow caching
* Reliable worry-free BLE connections
* Composable Flow utilities
* QR generation components
* Version-agnostic support for Java 1.8 Collection & Map features under Kotlin
* And more!

For more info, check out the JavaDoc at one of the links below:

* https://regulad.github.io/regulib/common
* https://regulad.github.io/regulib/ble
* https://regulad.github.io/regulib/compose

## Installation

ReguLib is available on Maven Central.

Get the version from the badge above, and add the following to your `build.gradle.kts` per-module file:

```kotlin
dependencies {
    implementation("xyz.regulad:regulib-common:$version")
    implementation("xyz.regulad:regulib-ble:$version")
    implementation("xyz.regulad:regulib-compose:$version")
}
```

You may run into version conflicts if you use other libraries that depend on different versions of Kotlin or other
libraries. Check the `gradle/libs.versions.toml` for the versions of dependencies ReguLib relies on.

Due to the size of the libraries, it is highly recommended to set up ProGuard rules to remove unused code.
