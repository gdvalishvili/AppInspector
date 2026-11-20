import java.util.Properties
import com.android.build.api.artifact.SingleArtifact
import com.android.build.api.variant.BuiltArtifactsLoader
import java.io.File

// Signing data lives outside Git (keystore.properties in the project root)
val keystoreProperties = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

// App version: edit only version.properties (project root).
// versionName = "MAJOR.MINOR.PATCH", versionCode = MAJOR*10000 + MINOR*100 + PATCH (e.g. 3.1.0 -> 30100)
val versionProperties = Properties().apply {
    rootProject.file("version.properties").inputStream().use { load(it) }
}
val versionMajor = versionProperties.getProperty("MAJOR").trim().toInt()
val versionMinor = versionProperties.getProperty("MINOR").trim().toInt()
val versionPatch = versionProperties.getProperty("PATCH").trim().toInt()
require(versionMinor in 0..99 && versionPatch in 0..99) { "MINOR and PATCH must be 0-99 in version.properties" }
val appVersionName = "$versionMajor.$versionMinor.$versionPatch"
val appVersionCode = versionMajor * 10000 + versionMinor * 100 + versionPatch

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.androidx.room)
}

android {
    namespace = "com.gdvalishvili.appinspector"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.gdvalishvili.appinspector"
        minSdk = 26
        targetSdk = 37
        versionCode = appVersionCode
        versionName = appVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (keystoreProperties.isNotEmpty()) {
            create("release") {
                storeFile = file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            // R8: shrink + optimize code, remove unused resources
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfigs.findByName("release")?.let { signingConfig = it }
        }
        debug {
            // Same key as release: "Run" in Android Studio updates the installed app, data is kept
            signingConfigs.findByName("release")?.let { signingConfig = it }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
    androidResources {
        generateLocaleConfig = true
    }
}

room {
    // Exported schema JSON files: needed for safe migrations later. Commit this folder to Git.
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.room.runtime)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.glance.appwidget)
}

// ---- Named copy of the release APK: build/outputs/named_apk/release/AppInspector-<version>-release.apk ----
// AGP 9 no longer allows renaming the original APK; this copies it when the APK is built.
abstract class CopyNamedApk : DefaultTask() {
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val input: DirectoryProperty

    @get:OutputDirectory
    abstract val output: DirectoryProperty

    @get:Internal
    abstract val builtArtifactsLoader: Property<BuiltArtifactsLoader>

    @get:Input
    abstract val appName: Property<String>

    @get:Input
    abstract val buildTypeName: Property<String>

    @TaskAction
    fun copy() {
        val outDir = output.get().asFile
        outDir.deleteRecursively()
        outDir.mkdirs()
        val artifacts = builtArtifactsLoader.get().load(input.get())
            ?: throw GradleException("Cannot load APKs")
        artifacts.elements.forEach { apk ->
            val version = apk.versionName ?: "unknown"
            val target = File(outDir, "${appName.get()}-$version-${buildTypeName.get()}.apk")
            File(apk.outputFile).copyTo(target, overwrite = true)
            logger.lifecycle("Named APK: $target")
        }
    }
}

androidComponents {
    onVariants(selector().withBuildType("release")) { variant ->
        val copyTask = tasks.register<CopyNamedApk>("copyNamedApkFor${variant.name}") {
            appName.set("AppInspector")
            buildTypeName.set(variant.buildType ?: variant.name)
            output.set(layout.buildDirectory.dir("outputs/named_apk/${variant.name}"))
            builtArtifactsLoader.set(variant.artifacts.getBuiltArtifactsLoader())
        }
        // Runs automatically whenever the release APK is created
        variant.artifacts.use(copyTask).wiredWith { it.input }.toListenTo(SingleArtifact.APK)
    }
}