import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.example.sleepalertapp"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.sleepalertapp"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // --- [追加] local.properties から認証情報を読み込む ---
        val localProps = Properties()
        val localPropertiesFile = rootProject.file("local.properties")
        if (localPropertiesFile.exists()) {
            localProps.load(localPropertiesFile.inputStream())
        }

        // BuildConfigに値を埋め込む（値がない場合は空文字を入れる）
        buildConfigField("String", "GMAIL_USER", "\"${localProps.getProperty("GMAIL_USER") ?: ""}\"")
        buildConfigField("String", "GMAIL_PASS", "\"${localProps.getProperty("GMAIL_PASS") ?: ""}\"")
        // --- [追加ここまで] ---

        packaging {
            resources {
                excludes += "/META-INF/NOTICE.md"
                excludes += "/META-INF/LICENSE.md"
            }
        }
    }

    buildTypes {
        release {
            // [変更] 難読化を有効にする（セキュリティ向上のため）
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    // ... compileOptionsなどはそのまま ...
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        viewBinding = true
        // [追加] BuildConfigクラスの自動生成を有効にする
        buildConfig = true
    }
}

dependencies {
    implementation(libs.android.mail)
    implementation(libs.android.activation)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}