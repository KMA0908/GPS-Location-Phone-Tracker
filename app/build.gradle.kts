import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.google.services)
    id("kotlin-kapt")
    id("kotlin-parcelize")
}

val localSecrets = Properties().apply {
    val secretsFile = rootProject.file("secrets.properties")
    if (secretsFile.isFile) {
        secretsFile.inputStream().use { load(it) }
    }
}

val mapsApiKey = (
    providers.environmentVariable("MAPS_API_KEY").orNull
        ?: localSecrets.getProperty("MAPS_API_KEY")
).orEmpty().trim().takeIf { it.isNotEmpty() && it != "DEFAULT_API_KEY" }
    ?: throw GradleException(
        "MAPS_API_KEY is missing. Add it to the ignored secrets.properties file " +
            "or set MAPS_API_KEY in the build environment.",
    )

val releaseStoreFilePath = (
    providers.environmentVariable("RELEASE_STORE_FILE").orNull
        ?: localSecrets.getProperty("RELEASE_STORE_FILE")
).orEmpty()
val releaseStorePassword = (
    providers.environmentVariable("RELEASE_STORE_PASSWORD").orNull
        ?: localSecrets.getProperty("RELEASE_STORE_PASSWORD")
).orEmpty()
val releaseKeyAlias = (
    providers.environmentVariable("RELEASE_KEY_ALIAS").orNull
        ?: localSecrets.getProperty("RELEASE_KEY_ALIAS")
).orEmpty()
val releaseKeyPassword = (
    providers.environmentVariable("RELEASE_KEY_PASSWORD").orNull
        ?: localSecrets.getProperty("RELEASE_KEY_PASSWORD")
).orEmpty()
val releaseSigningConfigured = listOf(
    releaseStoreFilePath,
    releaseStorePassword,
    releaseKeyAlias,
    releaseKeyPassword,
).all(String::isNotBlank)

android {
    namespace = "com.nhn.gps.location.phone.tracker"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.nhn.gps.location.phone.tracker"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        manifestPlaceholders["appName"] = "GPS Location Phone Tracker"
        manifestPlaceholders["MAPS_API_KEY"] = mapsApiKey
        buildConfigField(
            "String",
            "ROUTES_FUNCTION_URL",
            "\"https://asia-southeast1-gps-location-phone.cloudfunctions.net/computeRoute\"",
        )
        manifestPlaceholders["admobAppId"] = "ca-app-pub-5889155949011891~7746138357"
    }

    flavorDimensions += "environment"
    productFlavors {
        create("production") {
            dimension = "environment"
            manifestPlaceholders["appName"] = "GPS Location Phone Tracker"
        }
        create("staging") {
            dimension = "environment"
            manifestPlaceholders["appName"] = "_GPS Location Phone Tracker"
        }
    }

    signingConfigs {
        if (releaseSigningConfigured) {
            create("release") {
                storeFile = rootProject.file(releaseStoreFilePath)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        debug {
            isDebuggable = true
            signingConfig = signingConfigs.getByName("debug")
        }
        release {
            signingConfigs.findByName("release")?.let { signingConfig = it }
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

}

// Firebase has a production client only. Keep staging buildable until its own
// google-services.json is added, without accidentally using production config.
tasks.configureEach {
    if (name.contains("Staging") && name.endsWith("GoogleServices")) {
        enabled = false
    }
}

dependencies {
    implementation(libs.androidx.camera.view)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.material)
    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.hilt.android)
    implementation(libs.play.services.maps)
    implementation(libs.kotlinx.coroutines.play.services)
    kapt(libs.hilt.compiler)

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.analytics)
    implementation(libs.firebase.functions)
    implementation(libs.firebase.config)
    implementation(libs.firebase.app.check.play.integrity)
    debugImplementation(libs.firebase.app.check.debug)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    implementation(libs.play.services.locations)
    implementation(libs.android.maps.utils)
    implementation(libs.glide)
    kapt(libs.glide.compiler)
    implementation(libs.zxing.core)
    implementation(libs.zxing.android.embedded)
    implementation(libs.androidx.recyclerview)
    implementation(libs.facebook.shimmer)
    implementation(libs.leansoft.ads)
    implementation(libs.ccp)
    implementation(libs.worldwind)
    coreLibraryDesugaring(libs.desugar.jdk.libs)
}
