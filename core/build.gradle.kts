plugins { alias(libs.plugins.kotlin.jvm) }
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
