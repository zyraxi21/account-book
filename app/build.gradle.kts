plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.androidx.room)
}

android {
    namespace = "io.github.zyraxi21.accountbook"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "io.github.zyraxi21.accountbook"
        minSdk = 34
        targetSdk = 37
        versionCode = 202610071
        versionName = "2026.10.07.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        ndk {
            abiFilters += "arm64-v8a"
        }
    }

    buildTypes {
        release {
            // 自用版本沿用本机开发签名，覆盖安装保留账本和 Keystore 密钥。
            signingConfig = signingConfigs.getByName("debug")
            isDebuggable = false
            optimization {
                enable = true
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
    sourceSets {
        getByName("test").kotlin.directories.add("src/sharedTest/java")
        getByName("androidTest").kotlin.directories.add("src/sharedTest/java")
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.compose.foundation)
    // 提供系统动态取色、涟漪与 Snackbar，应用控件与主题使用 Fluent。
    implementation(libs.androidx.compose.material3)
    // Fluent 复选框等控件会调用 Compose Material 图标，需显式提供运行时依赖。
    implementation(libs.androidx.compose.material.icons.core)
    // FluentTheme 在运行时使用 observeAsState，需显式提供 Compose 的 LiveData 适配模块。
    implementation(libs.androidx.compose.runtime.livedata)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.sqlite)
    implementation(libs.sqlcipher)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.fluent.core)
    implementation(libs.fluent.controls)
    implementation(libs.fluent.icons)
    implementation(libs.fluent.topappbars)
    implementation(libs.fluent.menus)
    implementation(libs.fluent.calendar)
    implementation(libs.fluent.drawer)
    ksp(libs.androidx.room.compiler)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
