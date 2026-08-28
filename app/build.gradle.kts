plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.wxjxpp.neiro"
    // AGP 8.13.x 支持到 36；升 compileSdk 需要先升 AGP
    compileSdk = 36

    defaultConfig {
        applicationId = "com.wxjxpp.neiro"
        minSdk = 24
        targetSdk = 36
        versionCode = 12
        versionName = "0.4.7"
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            // Release 开启 R8 代码压缩、优化与混淆。
            isMinifyEnabled = true
            isShrinkResources = true
            // 不保留完整 app 包，避免 R8 规则形同虚设；仅为运行时反射入口保留必要类。
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            // 未配置签名时用 debug 签名，保证 Actions 能直接产出可安装 APK
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)

    // Compose 基础（不使用 compose-bom：BOM 会把 ui 抬到需要 compileSdk 37 的版本）
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.foundation)
    implementation(libs.androidx.animation)
    implementation(libs.androidx.material.icons.extended)

    // Material 3 Expressive
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material3.window.size)
    implementation(libs.androidx.graphics.shapes)

    // 播放内核
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.session)
    implementation(libs.androidx.media3.common)
    // 下载文件标签写入（ID3/Vorbis/MP4：标题/歌手/专辑/封面/歌词）
    implementation(libs.net.jaudiotagger)
    // SAF 目录访问（自定义下载目录）
    implementation(libs.androidx.documentfile)

    // 落库与设置
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore.preferences)

    // 自定义音源 JS 引擎
    implementation(libs.quickjs.wrapper)

    // 封面加载
    implementation(libs.coil.compose)
    // Baseline Profile 安装器：APK 里带的 baseline-prof.txt（Compose/material3 自带，
    // 见 aar 内 baseline-prof.txt）需要它才会在安装/首启时交给 ART 预编译。
    // 没有它，Compose 首次滚动全靠解释执行 + JIT，就是"第一次滑都很卡"的原因。
    implementation(libs.androidx.profileinstaller)
    // Haze 硬件加速毛玻璃（Expr 实验）
    implementation(libs.haze)
    // Miuix RuntimeShader：About 页使用原生 shader 流光效果（API 33+）
    implementation(libs.miuix.shader)
    // accompanist 歌词 core 的 KRC 元数据解码需要
    implementation(libs.kotlinx.serialization.json)
    // accompanist 歌词 UI 的平滑圆角组件
    implementation(libs.gaze.capsule)
    debugImplementation(libs.androidx.ui.tooling)
    // 纯 JVM 单元测试（歌词解析回归）
    testImplementation(libs.junit)
}