import java.util.Properties

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
        versionCode = 202610081
        versionName = "2026.10.08.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        ndk {
            abiFilters += "arm64-v8a"
        }
    }

    // 读取本地签名配置；缺失或未填写时构建直接失败，不退回 debug 签名。
    val keystorePropertiesFile = rootProject.file("keystore.properties")
    val keystoreProperties = Properties().apply {
        if (keystorePropertiesFile.exists()) {
            keystorePropertiesFile.inputStream().use { load(it) }
        }
    }

    signingConfigs {
        create("release") {
            val storePath = keystoreProperties.getProperty("storeFile")
            require(!storePath.isNullOrBlank()) {
                "缺少 keystore.properties 中的 storeFile。请复制 keystore.properties.example 并填写本机路径与口令。"
            }
            // 相对路径按根目录解析，便于换机器时只改配置不改脚本。
            storeFile = rootProject.file(storePath)
            listOf("storePassword", "keyAlias", "keyPassword").forEach { name ->
                require(!keystoreProperties.getProperty(name).isNullOrBlank()) {
                    "keystore.properties 中的 $name 未填写，请补全后重新构建。"
                }
            }
            storePassword = keystoreProperties.getProperty("storePassword")
            keyAlias = keystoreProperties.getProperty("keyAlias")
            keyPassword = keystoreProperties.getProperty("keyPassword")
        }
    }

    buildTypes {
        release {
            // 发布签名来自 keystore.properties，不用 debug 签名——
            // 后者口令公开，等同于任何人都能伪造同签名 APK。
            signingConfig = signingConfigs.getByName("release")
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
    implementation(libs.androidx.compose.animation)
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
    // 下载进度等线性进度条使用 Fluent 控件，与其余界面保持一致。
    implementation(libs.fluent.progress)
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
