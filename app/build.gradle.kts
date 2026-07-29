plugins {
    id("com.android.application")
}

android {
    namespace = "com.vn.qrscanner"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.vn.qrscanner"
        minSdk = 28
        targetSdk = 37
        versionCode = 10
        versionName = "3.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            resValue("string", "RESULT_BANNER_ADS_UNIT", "ca-app-pub-4182590278581659/6078741315")
        }
        debug {
            resValue("string", "RESULT_BANNER_ADS_UNIT", "ca-app-pub-3940256099942544/6300978111")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
        resValues = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)

    //Ads
    implementation(libs.play.services.ads)

    //QR scanner
    implementation(project(":barcodescanner"))
    implementation(libs.zxing.core)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}