import java.io.File
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.nielit.tomatoai"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.nielit.tomatoai"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64")
        }

        externalNativeBuild {
            cmake {
                cFlags += "-Wl,-z,max-page-size=16384"
                cppFlags += "-Wl,-z,max-page-size=16384"
            }
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("debug")
        }
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    androidResources {
        noCompress += listOf("pte", "tflite", "bin")
    }

    packaging {
        resources {
            excludes += "/META-INF/AL2.0"
            excludes += "/META-INF/LGPL2.1"
            pickFirsts += "AndroidManifest.xml"
        }
        jniLibs {
            useLegacyPackaging = true
        }
    }
}

val zipalignApk16k = tasks.register("zipalignApk16k") {
    group = "alignment"
    description = "Aligns the generated APK to 16KB page boundaries and re-signs it with debug certificate for manual installation."

    val androidExt = project.extensions.findByName("android") as? com.android.build.gradle.BaseExtension
    val sdkDir = androidExt?.sdkDirectory
    val apkDirFile = layout.buildDirectory.dir("outputs/apk/debug").get().asFile

    doLast {
        val buildToolsDir = File(sdkDir, "build-tools")
        val latestBuildTools = buildToolsDir.listFiles()?.maxByOrNull { it.name }
        val isWin = System.getProperty("os.name").lowercase().contains("win")
        val zipalignExe = File(latestBuildTools, if (isWin) "zipalign.exe" else "zipalign")
        val apksignerTool = File(latestBuildTools, if (isWin) "apksigner.bat" else "apksigner")

        val userHome = System.getProperty("user.home")
        val debugKeystore = File(userHome, ".android/debug.keystore")

        val inputApk = File(apkDirFile, "app-debug.apk")
        val alignedApk = File(apkDirFile, "app-debug-16k-aligned.apk")

        if (inputApk.exists() && zipalignExe != null && zipalignExe.exists()) {
            println("Step 1: Aligning APK entries to 16KB page size boundaries using ${zipalignExe.name}...")
            ProcessBuilder(zipalignExe.absolutePath, "-f", "-P", "16", "4", inputApk.absolutePath, alignedApk.absolutePath)
                .inheritIO()
                .start()
                .waitFor()

            if (alignedApk.exists() && apksignerTool != null && apksignerTool.exists() && debugKeystore.exists()) {
                println("Step 2: Signing 16KB aligned APK with debug certificate (${debugKeystore.name})...")
                val javaHome = System.getProperty("java.home")

                val pbSign = ProcessBuilder(
                    apksignerTool.absolutePath, "sign",
                    "--ks", debugKeystore.absolutePath,
                    "--ks-pass", "pass:android",
                    "--key-pass", "pass:android",
                    "--ks-key-alias", "androiddebugkey",
                    alignedApk.absolutePath
                )
                if (javaHome != null) pbSign.environment()["JAVA_HOME"] = javaHome
                pbSign.inheritIO().start().waitFor()

                println("Step 3: Verifying signature and 16KB alignment...")
                val pbVerify = ProcessBuilder(apksignerTool.absolutePath, "verify", alignedApk.absolutePath)
                if (javaHome != null) pbVerify.environment()["JAVA_HOME"] = javaHome
                pbVerify.inheritIO().start().waitFor()

                val pbAlignCheck = ProcessBuilder(zipalignExe.absolutePath, "-c", "-P", "16", "4", alignedApk.absolutePath)
                pbAlignCheck.inheritIO().start().waitFor()

                // Replace app-debug.apk with signed + 16KB aligned version for one-click deployment
                alignedApk.copyTo(inputApk, overwrite = true)
                println("SUCCESS: Signed and 16KB aligned APK created at:\n - ${alignedApk.absolutePath}\n - ${inputApk.absolutePath}")
            } else {
                println("16KB alignment finished, but apksigner or debug.keystore was not found.")
            }
        } else {
            println("Input APK or zipalign tool not found.")
        }
    }
}

// Automatically trigger 16KB alignment after packaging, assembly, or installation tasks
tasks.matching { it.name == "packageDebug" || it.name == "assembleDebug" }.configureEach {
    finalizedBy(zipalignApk16k)
}

tasks.matching { it.name.startsWith("install") }.configureEach {
    dependsOn(zipalignApk16k)
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_11)
    }
}

// Dependencies
dependencies {
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.core.ktx)
    implementation(libs.material)

    // The core AI engines
    implementation(libs.tensorflow.lite)
    implementation(libs.tensorflow.lite.api)
    implementation(libs.tensorflow.lite.support)

    // Coroutines & Lifecycle for background AI execution
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // ExecuTorch / PyTorch Android dependencies & Local libs folder support (.aar / .so)
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar", "*.aar"))))
    implementation("org.pytorch:pytorch_android:2.1.0")

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
