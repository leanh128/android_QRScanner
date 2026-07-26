plugins {
    id("com.android.application")
}

android {
    namespace = "com.vn.qrscanner"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.vn.qrscanner"
        minSdk = 23
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
    implementation("androidx.core:core-ktx:1.19.0")
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("com.google.android.material:material:1.14.0")
    implementation("androidx.constraintlayout:constraintlayout:2.2.1")

    //Ads
    implementation("com.google.android.gms:play-services-ads:25.4.0")

    //QR scanner
    implementation("me.dm7.barcodescanner:zxing:1.9.8")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
}