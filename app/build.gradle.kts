plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.signal.reader"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.signal.reader"
        minSdk = 26
        targetSdk = 35
        versionCode = 22
        versionName = "1.4.17"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    kotlinOptions {
        jvmTarget = "21"
        freeCompilerArgs += listOf(
            "-opt-in=androidx.compose.animation.ExperimentalSharedTransitionApi"
        )
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
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.animation)
    implementation(libs.androidx.navigation.compose)

    // Room
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // WorkManager
    implementation(libs.androidx.work.runtime.ktx)

    // Networking & parsing
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.jsoup)

    // Images
    implementation(libs.coil.compose)

    // Custom Tabs
    implementation(libs.androidx.browser)

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)

    // YouTube Player (Official IFrame API wrapper for Android)
    implementation("com.pierfrancescosoffritti.androidyoutubeplayer:core:12.1.1")

    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation("junit:junit:4.13.2")
    testImplementation("xmlpull:xmlpull:1.1.3.4d_b4_min")
    testImplementation("net.sf.kxml:kxml2:2.3.0")
}

val syncCatalogIntentFilters = tasks.register("syncCatalogIntentFilters") {
    val catalogFile = file("src/main/assets/catalog.json")
    val manifestFile = file("src/main/AndroidManifest.xml")
    inputs.file(catalogFile)
    outputs.file(manifestFile)
    doLast {
        if (!catalogFile.exists() || !manifestFile.exists()) return@doLast
        val jsonText = catalogFile.readText()
        val hostRegex = """"siteUrl"\s*:\s*"https?://([^/"]+)""".toRegex()
        val hosts = hostRegex.findAll(jsonText).map { match ->
            var host = match.groupValues[1].lowercase()
            if (host.startsWith("www.")) {
                host = host.removePrefix("www.")
            }
            host
        }.toSet().sorted()

        val filterBuilder = StringBuilder()
        filterBuilder.append("<!-- BEGIN GENERATED CATALOG DOMAIN FILTERS -->\n")
        filterBuilder.append("            <intent-filter>\n")
        filterBuilder.append("                <action android:name=\"android.intent.action.VIEW\" />\n")
        filterBuilder.append("                <category android:name=\"android.intent.category.DEFAULT\" />\n")
        filterBuilder.append("                <category android:name=\"android.intent.category.BROWSABLE\" />\n")
        filterBuilder.append("                <data android:scheme=\"http\" />\n")
        filterBuilder.append("                <data android:scheme=\"https\" />\n")
        for (host in hosts) {
            filterBuilder.append("                <data android:host=\"$host\" />\n")
            filterBuilder.append("                <data android:host=\"*.$host\" />\n")
        }
        filterBuilder.append("            </intent-filter>\n")
        filterBuilder.append("            <!-- END GENERATED CATALOG DOMAIN FILTERS -->")

        val generatedXml = filterBuilder.toString()
        val manifestContent = manifestFile.readText()
        val beginMarker = "<!-- BEGIN GENERATED CATALOG DOMAIN FILTERS -->"
        val endMarker = "<!-- END GENERATED CATALOG DOMAIN FILTERS -->"

        if (manifestContent.contains(beginMarker) && manifestContent.contains(endMarker)) {
            val before = manifestContent.substringBefore(beginMarker)
            val after = manifestContent.substringAfter(endMarker)
            val newContent = before + generatedXml + after
            if (newContent != manifestContent) {
                manifestFile.writeText(newContent)
                println("Generated intent filters for ${hosts.size} catalog domains into AndroidManifest.xml")
            }
        }
    }
}

tasks.named("preBuild") {
    dependsOn(syncCatalogIntentFilters)
}
