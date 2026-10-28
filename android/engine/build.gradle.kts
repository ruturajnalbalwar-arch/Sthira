plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.kotlin.plugin.serialization")
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.2")
    testImplementation("junit:junit:4.13.2")
}

tasks.test {
    // The TypeScript engine is the reference; web/src/lib/sthira/__tests__/golden.test.ts generates this file.
    systemProperty("sthira.golden", rootProject.file("../shared/golden/chair-stand.golden.json").absolutePath)
    systemProperty("sthira.params", rootProject.file("../web/src/lib/sthira/params/params.json").absolutePath)
    systemProperty("sthira.norms", rootProject.file("../web/src/lib/sthira/norms/norms.json").absolutePath)
}
