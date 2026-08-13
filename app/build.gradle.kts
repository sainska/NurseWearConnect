import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    id("com.google.gms.google-services")
}

val versionPropsFile = rootProject.file("version.properties")
val versionProps = Properties().apply {
    if (versionPropsFile.exists()) {
        load(versionPropsFile.inputStream())
    }
}

val vMajor = versionProps.getProperty("VERSION_MAJOR", "1").toInt()
val vMinor = versionProps.getProperty("VERSION_MINOR", "0").toInt()
val vPatch = versionProps.getProperty("VERSION_PATCH", "0").toInt()
val vBuild = versionProps.getProperty("VERSION_BUILD", "1").toInt()

val appVersionName = "$vMajor.$vMinor.$vPatch"
val appVersionCode = vMajor * 10000 + vMinor * 100 + vPatch

// Helper to update version.properties
fun updateVersionProperty(type: String) {
    val props = Properties()
    val file = rootProject.file("version.properties")
    if (file.exists()) {
        file.inputStream().use { props.load(it) }
        
        var major = props.getProperty("VERSION_MAJOR", "1").toInt()
        var minor = props.getProperty("VERSION_MINOR", "0").toInt()
        var patch = props.getProperty("VERSION_PATCH", "0").toInt()
        var build = props.getProperty("VERSION_BUILD", "1").toInt()
        
        when (type.lowercase()) {
            "major" -> { major++; minor = 0; patch = 0 }
            "minor" -> { minor++; patch = 0 }
            "patch" -> { patch++ }
        }
        build++
        
        props.setProperty("VERSION_MAJOR", major.toString())
        props.setProperty("VERSION_MINOR", minor.toString())
        props.setProperty("VERSION_PATCH", patch.toString())
        props.setProperty("VERSION_BUILD", build.toString())
        
        file.outputStream().use { props.store(it, "Auto-updated by Build System") }
        println("Version updated to $major.$minor.$patch (Build $build)")
    }
}

tasks.register("incrementVersion") {
    group = "versioning"
    description = "Increments version based on -PincType=[major|minor|patch]"
    doLast {
        val type = if (project.hasProperty("incType")) project.property("incType").toString() else "patch"
        updateVersionProperty(type)
    }
}

// Logic to detect changes and auto-version locally
tasks.register("autoVersionLocal") {
    group = "versioning"
    description = "Increments version locally for development builds"
    doLast {
        val isCI = System.getenv("GITHUB_ACTIONS") != null
        if (!isCI) {
            println("Auto-incrementing build number for local run...")
            updateVersionProperty("patch")
        }
    }
}

// Ensure version is updated before every build in debug mode
tasks.named("preBuild") {
    dependsOn("autoVersionLocal")
}

android {
    namespace = "com.example.nursewearconnect"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.nursewearconnect"
        minSdk = 24
        targetSdk = 35
        versionCode = appVersionCode
        versionName = appVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        
        // Optimize: Include only necessary languages to reduce resource size
        androidResources {
            localeFilters += "en"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            // Optional: Enable minification for debug to test its impact,
            // but keep it false by default for build speed.
            isMinifyEnabled = false
        }
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            // Exclude unnecessary licenses and metadata to save space
            excludes += "META-INF/DEPENDENCIES"
            excludes += "META-INF/LICENSE*"
            excludes += "META-INF/NOTICE*"
            excludes += "META-INF/*.kotlin_module"
        }
        jniLibs {
            pickFirsts += "**/lib*.so"
            pickFirsts += "**/lib*.so.dbg"
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

tasks.withType<KotlinJvmCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    coreLibraryDesugaring(libs.desugar.jdk.libs)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.browser)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material3.windowSizeClass)
    implementation(libs.androidx.compose.material3.adaptive)
    implementation(libs.androidx.compose.material3.adaptive.layout)
    implementation(libs.androidx.compose.material3.adaptive.navigation)
    implementation(libs.androidx.compose.material3.adaptive.navigation.suite)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.security.crypto)
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)
    implementation(libs.okhttp.logging)
    implementation(libs.coil.compose)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.work.runtime)
    implementation(libs.zxing.core)
    ksp(libs.androidx.room.compiler)
    implementation(libs.kotlinx.datetime)

    // Firebase
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)
    implementation(libs.firebase.analytics)

    // Supabase & Ktor
    implementation(platform(libs.supabase.bom))
    implementation(libs.supabase.postgrest)
    implementation(libs.supabase.auth)
    implementation(libs.supabase.realtime)
    implementation(libs.supabase.storage)
    implementation(libs.ktor.client.android)
    implementation(libs.ktor.client.logging)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

tasks.register("unitTestClasses") {
    dependsOn("compileDebugUnitTestSources")
    if (tasks.findByName("compileReleaseUnitTestSources") != null) {
        dependsOn("compileReleaseUnitTestSources")
    }
}

tasks.register("androidTestClasses") {
    dependsOn("compileDebugAndroidTestSources")
}

// Task to generate an upload trigger script instead of direct upload
tasks.register("uploadApkToSupabase") {
    group = "deployment"
    description = "Generates a script to trigger the APK upload to Supabase"
    dependsOn("assembleDebug")
    
    doLast {
        val props = Properties()
        val file = rootProject.file("version.properties")
        if (file.exists()) file.inputStream().use { props.load(it) }
        
        val major = props.getProperty("VERSION_MAJOR", "1")
        val minor = props.getProperty("VERSION_MINOR", "0")
        val patch = props.getProperty("VERSION_PATCH", "0")
        val currentVersion = "$major.$minor.$patch"
        val prevPatch = patch.toInt() - 1
        
        val anonKey = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InRycHNlanphc2JmcWxzaHJiYmFlIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NzU4NDg4NTksImV4cCI6MjA5MTQyNDg1OX0.oD0zM5VDLXxt1onGsqCYo0HGh51bskWZjCFH5boXxSw"
        val baseUrl = "https://trpsejzasbfqlshrbbae.supabase.co/storage/v1/object/deployments"

        val apkDir = layout.buildDirectory.dir("outputs/apk/debug").get().asFile
        var apkFile = layout.buildDirectory.file("outputs/apk/debug/app-debug.apk").get().asFile
        if (!apkFile.exists()) {
            val foundApk = apkDir.listFiles()?.find { it.name.endsWith(".apk") && !it.name.contains("unaligned") }
            if (foundApk != null) apkFile = foundApk
        }

        if (apkFile.exists()) {
            val relativeApkPath = apkFile.relativeTo(rootProject.projectDir).path.replace("\\", "/")
            
            // Generate Shell Script
            val shFile = rootProject.file("supabase_upload.sh")
            val shContent = """
                #!/bin/bash
                echo "Starting APK Upload for v$currentVersion..."
                
                # Delete old latest
                curl -X DELETE -H "Authorization: Bearer $anonKey" -H "apikey: $anonKey" $baseUrl/nursewear-connect-latest.apk
                
                ${if (prevPatch >= 0) "curl -X DELETE -H \"Authorization: Bearer $anonKey\" -H \"apikey: $anonKey\" $baseUrl/nursewear-connect-v$major.$minor.$prevPatch.apk" else ""}
                
                # Upload Versioned
                curl -X POST -H "Authorization: Bearer $anonKey" -H "apikey: $anonKey" -H "Content-Type: application/vnd.android.package-archive" --data-binary "@$relativeApkPath" "$baseUrl/nursewear-connect-v$currentVersion.apk?upsert=true"
                
                # Upload Latest
                curl -X POST -H "Authorization: Bearer $anonKey" -H "apikey: $anonKey" -H "Content-Type: application/vnd.android.package-archive" --data-binary "@$relativeApkPath" "$baseUrl/nursewear-connect-latest.apk?upsert=true"
                
                echo "Upload Complete!"
            """.trimIndent()
            shFile.writeText(shContent)
            
            // Generate Batch Script
            val batFile = rootProject.file("supabase_upload.bat")
            val batContent = """
                @echo off
                echo Starting APK Upload for v$currentVersion...
                
                curl -X DELETE -H "Authorization: Bearer %SAME%" -H "apikey: %SAME%" $baseUrl/nursewear-connect-latest.apk
                
                ${if (prevPatch >= 0) "curl -X DELETE -H \"Authorization: Bearer %SAME%\" -H \"apikey: %SAME%\" $baseUrl/nursewear-connect-v$major.$minor.$prevPatch.apk" else ""}
                
                curl -X POST -H "Authorization: Bearer %SAME%" -H "apikey: %SAME%" -H "Content-Type: application/vnd.android.package-archive" --data-binary "@$relativeApkPath" "$baseUrl/nursewear-connect-v$currentVersion.apk?upsert=true"
                
                curl -X POST -H "Authorization: Bearer %SAME%" -H "apikey: %SAME%" -H "Content-Type: application/vnd.android.package-archive" --data-binary "@$relativeApkPath" "$baseUrl/nursewear-connect-latest.apk?upsert=true"
                
                echo Upload Complete!
                pause
            """.trimIndent().replace("%SAME%", anonKey)
            batFile.writeText(batContent)

            println("\n[SKIP] Direct upload skipped as requested.")
            println("[TRIGGER] Created upload scripts: supabase_upload.sh and supabase_upload.bat")
            println("Run these files to perform the actual upload to Supabase.")
        } else {
            println("Error: APK not found to create trigger script.")
        }
    }
}

// Automatic trigger generation is kept, but direct upload is skipped
project.afterEvaluate {
    tasks.findByName("assembleDebug")?.finalizedBy("uploadApkToSupabase")
}
