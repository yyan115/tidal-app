plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.pitest)
}
kotlin { jvmToolchain(21) }
dependencies {
    implementation(libs.okhttp)
    implementation(libs.coroutines.core)
    compileOnly(libs.json)
    testImplementation(libs.json)
    testImplementation(libs.junit)
    testImplementation(libs.mockwebserver)
    testImplementation(libs.coroutines.test)
}
tasks.test { testLogging { events("failed", "skipped") } }

// Mutation testing: `./gradlew :core:pitest` changes the code in small ways, such as turning a comparison round or removing a call, and runs the tests on each change.
// A change that no test notices is a fault that the tests would let through. The share of changes that the tests notice is the mutation score, which says how good
// the tests are, where code coverage says only which code they run. The report is in core/build/reports/pitest.
pitest {
    pitestVersion.set("1.17.0")
    // The share of changes that the tests notice. It was 68% when this check was added and 70% after the tests of the requests of the client, and it may only go up, so the build fails below it.
    mutationThreshold.set(71)
    targetClasses.set(listOf("io.github.yyan115.opentune.core.*"))
    targetTests.set(listOf("io.github.yyan115.opentune.core.*"))
    threads.set(2)
    outputFormats.set(listOf("HTML", "XML"))
    timestampedReports.set(false)
    // Kotlin writes checks of its own into the code, and a change to them is not a fault of ours.
    excludedMethods.set(listOf("hashCode", "equals", "toString", "copy", "component*"))
    avoidCallsTo.set(listOf("kotlin.jvm.internal", "kotlin.ranges", "kotlin.collections"))
}
