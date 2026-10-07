import com.vanniktech.maven.publish.SonatypeHost

plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("com.vanniktech.maven.publish")
}

android {
    namespace = "com.verifyblind.sdk"
    compileSdk = 34

    defaultConfig {
        minSdk = 24
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
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
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")

    // HTTP / REST
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")

    // Kotlin Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
}

// Maven Central: com.verifyblind:verifyblind-android (koordinatlar gradle.properties'te).
// Yayın GitHub Actions'tan (.github/workflows/publish.yml) etiketle yapılır; imza anahtarı ve
// Central Portal token'ı repo secret'larındadır, depoda hiçbir sır yoktur.
mavenPublishing {
    publishToMavenCentral(SonatypeHost.CENTRAL_PORTAL)
    signAllPublications()

    pom {
        name.set("VerifyBlind Android SDK")
        description.set("Android SDK for VerifyBlind zero-knowledge identity verification: prove age or uniqueness with a chipped Turkish ID card without sharing personal data.")
        inceptionYear.set("2026")
        url.set("https://github.com/VerifyBlind/sdk-android")
        licenses {
            license {
                name.set("The Apache License, Version 2.0")
                url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                distribution.set("repo")
            }
        }
        developers {
            developer {
                id.set("verifyblind")
                name.set("VerifyBlind")
                email.set("support@verifyblind.com")
                url.set("https://verifyblind.com")
            }
        }
        scm {
            url.set("https://github.com/VerifyBlind/sdk-android")
            connection.set("scm:git:git://github.com/VerifyBlind/sdk-android.git")
            developerConnection.set("scm:git:ssh://git@github.com/VerifyBlind/sdk-android.git")
        }
    }
}
