// Versions match what is already in the local Gradle cache (AGP 8.5 + Gradle 8.7 + Kotlin 1.9.22 + Compose compiler 1.5.8).
plugins {
    id("com.android.application") version "8.5.0" apply false
    id("org.jetbrains.kotlin.android") version "1.9.22" apply false
    id("org.jetbrains.kotlin.jvm") version "1.9.22" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "1.9.22" apply false
}
