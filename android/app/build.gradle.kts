import java.net.URI

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.devtools.ksp")
}

val productionApiUrl = providers.gradleProperty("financeProductionApiUrl")
    .orElse("https://finance-api.demo.xichugeek.com/").get()
val releaseStorePath = providers.environmentVariable("FINANCE_KEYSTORE_FILE").orNull
val releaseStorePassword = providers.environmentVariable("FINANCE_KEYSTORE_PASSWORD").orNull
val releaseKeyAlias = providers.environmentVariable("FINANCE_KEY_ALIAS").orNull
val releaseKeyPassword = providers.environmentVariable("FINANCE_KEY_PASSWORD").orNull
val releaseSigningReady = listOf(releaseStorePath, releaseStorePassword, releaseKeyAlias, releaseKeyPassword).all { !it.isNullOrBlank() }
val validateReleaseSigning = tasks.register("validateReleaseSigning") {
    doLast {
        require(releaseSigningReady) { "Release signing requires the four FINANCE_KEYSTORE/KEY environment variables; never use Debug signing" }
        val keyFile = file(requireNotNull(releaseStorePath)).canonicalFile
        require(keyFile.isFile && !keyFile.toPath().startsWith(rootProject.projectDir.parentFile.canonicalFile.toPath())) {
            "Release keystore must exist outside the repository"
        }
        println("RELEASE_SIGNING_CONFIG_GUARD = PASS")
    }
}
val validateProductionApi = tasks.register("validateProductionApi") {
    doLast {
        val uri = URI(productionApiUrl)
        val host = uri.host?.lowercase() ?: ""
        require(uri.scheme == "https" && uri.userInfo == null && uri.query == null && uri.fragment == null && productionApiUrl.endsWith("/")) {
            "Release API must be an HTTPS base URL without credentials, query or fragment"
        }
        require(host.isNotBlank() && host !in listOf("localhost", "127.0.0.1", "::1", "[::1]", "10.0.2.2") &&
            !host.endsWith(".localhost") && !host.endsWith(".local") && !host.matches(Regex("[0-9.]+")) &&
            ':' !in host) { "Release API must use a public HTTPS domain" }
        println("PRODUCTION_API_URL_GUARD = PASS")
    }
}
tasks.configureEach { if (name == "preReleaseBuild") dependsOn(validateProductionApi, validateReleaseSigning) }

android {
    namespace = "com.xichugeek.finance"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.xichugeek.finance"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "1.0.1"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    if (releaseSigningReady) {
        signingConfigs.create("financeRelease") {
            storeFile = file(requireNotNull(releaseStorePath))
            storePassword = releaseStorePassword
            keyAlias = releaseKeyAlias
            keyPassword = releaseKeyPassword
            storeType = "PKCS12"
        }
    }
    testBuildType = if (providers.gradleProperty("financeReleaseValidation").orNull == "true") "release" else "debug"

    buildTypes {
        debug {
            buildConfigField("String", "API_BASE_URL", "\"http://10.0.2.2:8000/\"")
        }
        release {
            isMinifyEnabled = false
            if (releaseSigningReady) signingConfig = signingConfigs.getByName("financeRelease")
            buildConfigField("String", "API_BASE_URL", "\"$productionApiUrl\"")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    // One keyword catalog is packaged by both the Backend and Android.
    sourceSets.getByName("main").assets.directories.add(file("../../backend/app/data").absolutePath)
    sourceSets.getByName("androidTest").assets.directories.add(file("schemas").absolutePath)
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.09.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.3")
    implementation("androidx.navigation:navigation-compose:2.9.3")

    val roomVersion = "2.8.5"
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    ksp("androidx.room:room-compiler:$roomVersion")

    implementation("com.squareup.retrofit2:retrofit:3.0.0")
    implementation("com.squareup.retrofit2:converter-kotlinx-serialization:3.0.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
    implementation("androidx.datastore:datastore-preferences:1.1.7")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation(composeBom)
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
