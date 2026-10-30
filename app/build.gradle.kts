plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.compiler)
}
val releaseVersion = rootProject.file("VERSION").readText().trim()
val versionParts = releaseVersion.split('.').map(String::toInt)
require(versionParts.size == 3 && versionParts.all { it in 0..999 })
val noticeAssets = layout.buildDirectory.dir("generated/noticeAssets")
val copyNotices = tasks.register<Sync>("copyNotices") {
    from(rootProject.file("LICENSE"), rootProject.file("THIRD_PARTY_NOTICES.md"), rootProject.file("docs/licenses/Apache-2.0.txt"))
    into(noticeAssets.map { it.dir("licenses") })
}
android {
    namespace = "io.github.yyan115.undertow"
    compileSdk = 36
    defaultConfig {
        applicationId = "io.github.yyan115.undertow"
        minSdk = 26
        targetSdk = 36
        versionCode = versionParts[0] * 1_000_000 + versionParts[1] * 1_000 + versionParts[2]
        versionName = releaseVersion
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    val releaseKeyPath = providers.environmentVariable("UNDERTOW_KEYSTORE").orNull
    if (releaseKeyPath != null) signingConfigs.create("release") {
        storeFile = file(releaseKeyPath)
        storePassword = providers.environmentVariable("UNDERTOW_STORE_PASSWORD").get()
        keyAlias = providers.environmentVariable("UNDERTOW_KEY_ALIAS").getOrElse("undertow")
        keyPassword = providers.environmentVariable("UNDERTOW_KEY_PASSWORD").get()
    }
    buildTypes {
        release {
            if (releaseKeyPath != null) signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        debug { applicationIdSuffix = ".debug" }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    buildFeatures { compose = true; buildConfig = true }
    sourceSets.getByName("main").assets.srcDir(noticeAssets)
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    testOptions { animationsDisabled = true }
    lint { abortOnError = true; checkDependencies = true }
}
tasks.named("preBuild") { dependsOn(copyNotices) }
kotlin { jvmToolchain(21) }
dependencies {
    implementation(project(":core"))
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.icons)
    implementation(libs.activity.compose)
    implementation(libs.browser)
    implementation(libs.lifecycle.compose)
    implementation(libs.lifecycle.viewmodel)
    implementation(libs.coroutines.android)
    implementation(libs.okhttp)
    implementation(libs.coil.compose)
    implementation(libs.coil.network)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.dash)
    implementation(libs.media3.session)
    implementation(libs.media3.datasource)
    implementation(libs.work.runtime)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.test)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.rules)
    debugImplementation(libs.compose.test.manifest)
    debugImplementation(libs.compose.tooling)
}
