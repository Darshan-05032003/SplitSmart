import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.room)
    alias(libs.plugins.kover)
}

// Firebase (Analytics + Crashlytics) is wired only when the gitignored google-services.json is present,
// so a clean clone without Firebase still builds. The code talks to AnalyticsTracker / CrashReporter interfaces.
val firebaseConfigured = file("google-services.json").exists()
if (firebaseConfigured) {
    apply(plugin = "com.google.gms.google-services")
    apply(plugin = "com.google.firebase.crashlytics")
}

// Local, gitignored configuration. LLM_API_KEY is the Gemini API key; it is read here and never logged.
val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}
fun localProperty(name: String): String? =
    (localProperties.getProperty(name) ?: System.getenv(name))?.trim()?.trim('"', '\'')?.trim()?.takeIf { it.isNotEmpty() }
val llmApiKey: String = localProperty("LLM_API_KEY") ?: ""

android {
    namespace = "com.darshan.splitsmart"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.darshan.splitsmart"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "LLM_API_KEY", "\"$llmApiKey\"")
        buildConfigField("boolean", "FIREBASE_CONFIGURED", "$firebaseConfigured")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.isReturnDefaultValues = true
    }

    lint {
        abortOnError = true
        warningsAsErrors = false
    }
}

kotlin {
    jvmToolchain(17)
}

room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.viewmodel.savedstate)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.androidx.room.runtime)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.mlkit.text.recognition)
    implementation(libs.okhttp)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.core)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.androidx.room.testing)
    testImplementation(libs.hilt.android.testing)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.okhttp.tls)
    kspTest(libs.hilt.compiler)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
}

// Stress / load simulations live in the `stress` package and the single real-Gemini smoke test in `realapi`.
// Both are excluded from the normal unit test run: `./gradlew stressTest` and `./gradlew realApiTest`.
val stressPackage = "com.darshan.splitsmart.stress.*"
val realApiPackage = "com.darshan.splitsmart.realapi.*"

tasks.withType<Test>().configureEach {
    if (name != "stressTest") filter { excludeTestsMatching(stressPackage) }
    if (name != "realApiTest") filter { excludeTestsMatching(realApiPackage) }
}

fun registerFilteredTestTask(taskName: String, pattern: String, descriptionText: String) =
    tasks.register<Test>(taskName) {
        description = descriptionText
        group = "verification"
        val unitTest = tasks.named<Test>("testDebugUnitTest").get()
        dependsOn(unitTest.dependsOn)
        testClassesDirs = unitTest.testClassesDirs
        classpath = unitTest.classpath
        filter {
            includeTestsMatching(pattern)
            isFailOnNoMatchingTests = false
        }
        maxHeapSize = "1g"
        outputs.upToDateWhen { false }
        testLogging {
            showStandardStreams = true
            events("passed", "failed")
        }
    }

registerFilteredTestTask("stressTest", stressPackage, "Runs the SplitSmart load/stress simulations (excluded from testDebugUnitTest).")
registerFilteredTestTask("realApiTest", realApiPackage, "Runs the single real Gemini smoke test (needs LLM_API_KEY; excluded from testDebugUnitTest).")

// Coverage: the ≥ 80 % line-coverage rule applies to the domain and data layers (generated code excluded).
kover {
    currentProject {
        instrumentation {
            disabledForTestTasks.addAll("stressTest", "realApiTest")
        }
    }
    reports {
        filters {
            includes {
                classes("com.darshan.splitsmart.domain.*", "com.darshan.splitsmart.data.*")
            }
            excludes {
                classes(
                    "*_Impl", "*_Impl\$*", "*_Factory", "*_Factory\$*", "*_HiltModules*", "*_MembersInjector",
                    "*\$\$serializer", "*ComposableSingletons*", "*.BuildConfig",
                )
                annotatedBy("androidx.compose.runtime.Composable", "dagger.Module", "dagger.hilt.InstallIn")
            }
        }
        verify {
            rule("domain and data line coverage") {
                minBound(80)
            }
        }
    }
}
