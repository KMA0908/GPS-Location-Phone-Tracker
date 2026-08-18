plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.google.services)
    id("kotlin-kapt")
    id("kotlin-parcelize")
}

val bundledMapsApiKey = "AIzaSyAH7TC-3rHOGxrlTigvbPpOoTcCkcnkp4c"
val mapsApiKey = providers.gradleProperty("MAPS_API_KEY").orElse(bundledMapsApiKey).get()
val routesApiKey = providers.gradleProperty("ROUTES_API_KEY").orElse(mapsApiKey).get()

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
        resValue("string", "maps_api_key", mapsApiKey)
        resValue("string", "routes_api_key", routesApiKey)
        manifestPlaceholders["admobAppId"] = "ca-app-pub-3940256099942544~3347511713"
    }

    flavorDimensions += "environment"
    productFlavors {
        create("production") {
            dimension = "environment"
            manifestPlaceholders["appName"] = "GPS Location Phone Tracker"
        }
        create("staging") {
            dimension = "environment"
            applicationIdSuffix = ".stg"
            versionNameSuffix = "-stg"
            manifestPlaceholders["appName"] = "_GPS Location Phone Tracker"
        }
    }

    buildTypes {
        debug {
            versionNameSuffix = "-debug"
            isDebuggable = true
        }
        release {
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
    implementation(libs.places)
    implementation(libs.kotlinx.coroutines.play.services)
    kapt(libs.hilt.compiler)

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.analytics)
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.database)
    implementation(libs.firebase.storage)
    implementation(libs.firebase.auth)
    implementation(libs.firebase.config)

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
