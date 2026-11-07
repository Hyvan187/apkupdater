import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.io.FileInputStream
import java.util.Properties
import java.security.MessageDigest


plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

android {
    namespace = "com.apkupdater"
    compileSdk = 37

    val buildNumber = System.getenv("BUILD_NUMBER").orEmpty()
    defaultConfig {
        applicationId = "com.hyvan187.apkupdater"
        minSdk = 23
        targetSdk = 37
        versionCode = 1008
        versionName = "0.6.0-rc3"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true
        val fingerprint = MessageDigest.getInstance("SHA-256")
        (fileTree("src/main").files + listOf(file("build.gradle.kts"))).filter { it.isFile }.sortedBy { it.relativeTo(projectDir).path }.forEach { source ->
            fingerprint.update(source.relativeTo(projectDir).path.toByteArray()); fingerprint.update(source.readBytes())
        }
        buildConfigField("String", "BUILD_FINGERPRINT", "\"" + fingerprint.digest().joinToString("") { "%02x".format(it) } + "\"")
    }

    signingConfigs {
        getByName("debug") {
            System.getenv("GLASS_DEBUG_KEYSTORE")?.let { storeFile = file(it) }
        }
        create("release") {
            val path = System.getenv("GLASS_KEYSTORE")
            if (path != null) {
                storeFile = file(path)
                storePassword = System.getenv("GLASS_STORE_PASSWORD")
                keyAlias = System.getenv("GLASS_KEY_ALIAS")
                keyPassword = System.getenv("GLASS_KEY_PASSWORD")
            }
            enableV3Signing = true
        }
    }
    dependenciesInfo { includeInApk = false; includeInBundle = false }

    splits {
        abi {
            isEnable = true
            reset()
            include("armeabi-v7a", "arm64-v8a", "x86_64")
            isUniversalApk = true
        }
    }

    buildTypes {
        getByName("release") {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            isDebuggable = false
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
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

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.all { task ->
            task.maxParallelForks = 1
            task.maxHeapSize = "1024m"
            task.forkEvery = 1
            task.jvmArgs("--add-opens=java.base/jdk.internal.access=ALL-UNNAMED", "--add-opens=java.base/java.io=ALL-UNNAMED", "--add-opens=java.base/sun.nio.ch=ALL-UNNAMED")
            task.systemProperty("robolectric.dependency.repo.url", "https://repo.maven.apache.org/maven2")
            listOf("http.proxyHost", "http.proxyPort", "https.proxyHost", "https.proxyPort", "javax.net.ssl.trustStore").forEach { key ->
                System.getProperty(key)?.let { task.systemProperty(key, it) }
            }
        }
    }

    lint {
        warning.addAll(arrayOf("ExtraTranslation", "MissingTranslation", "MissingQuantity"))
    }
}

dependencies {
    implementation("com.android.tools.build:apksig:9.1.1")

    val composeBom = platform("androidx.compose:compose-bom:2026.02.01")
    implementation(composeBom)
    implementation("androidx.activity:activity-compose:1.12.4")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.10.0")
    implementation("androidx.navigation:navigation-compose:2.9.7")
    implementation("androidx.navigation:navigation-runtime-ktx:2.9.7")
    implementation("androidx.tv:tv-foundation:1.0.0-alpha12")
    implementation("androidx.work:work-runtime-ktx:2.11.1")
    implementation("com.github.rumboalla.KryptoPrefs:kryptoprefs-gson:0.4.3")
    implementation("com.github.rumboalla.KryptoPrefs:kryptoprefs:0.4.3")
    implementation("com.github.topjohnwu.libsu:core:6.0.0")
    implementation("com.auroraoss:gplayapi:3.5.8")
    implementation("com.google.code.gson:gson:2.13.2")
    implementation("com.squareup.okhttp3:logging-interceptor:5.3.2")
    implementation("com.squareup.retrofit2:converter-gson:3.0.0")
    implementation("com.squareup.retrofit2:retrofit:3.0.0")
    implementation("io.coil-kt.coil3:coil-compose:3.4.0")
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.4.0")
    implementation("io.github.g00fy2:versioncompare:1.5.0")
    implementation("io.insert-koin:koin-android:4.1.1")
    implementation("io.insert-koin:koin-androidx-compose:4.1.1")
    implementation("org.jsoup:jsoup:1.24.1")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
    testImplementation("androidx.work:work-testing:2.11.1")
    testImplementation("org.robolectric:robolectric:4.17")
    testImplementation("androidx.test.espresso:espresso-core:3.7.0")
    testImplementation("androidx.compose.ui:ui-test-junit4")
    testImplementation("androidx.test:core-ktx:1.7.0")
    testImplementation("androidx.test.ext:junit:1.3.0")

    androidTestImplementation(composeBom)
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")

    debugImplementation("androidx.compose.ui:ui-test-manifest")
    debugImplementation("androidx.compose.ui:ui-tooling")

}
