plugins {
    id("com.android.application")
}

android {
    namespace = "com.alpg0.mycovision"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.alpg0.mycovision"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    androidResources {
        noCompress += listOf("tflite")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    // Core
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("androidx.activity:activity:1.13.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.cardview:cardview:1.0.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")

    // Material Design
    implementation("com.google.android.material:material:1.13.0")

    // TFLite (LiteRT)
    implementation("com.google.ai.edge.litert:litert:2.1.0")

    // ML Kit — mushroom presence detection
    implementation("com.google.mlkit:image-labeling:17.0.9")

    // Room (local scan history)
    implementation("androidx.room:room-runtime:2.6.1")
    annotationProcessor("androidx.room:room-compiler:2.6.1")

    // Testing
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
}
