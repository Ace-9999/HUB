plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}
android {
    namespace = "dev.personalhub.app"
    compileSdk = 36
    defaultConfig {
        applicationId = "dev.personalhub"
        minSdk = 29
        targetSdk = 36
        versionCode = 2
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildTypes {
        create("fast") {
            initWith(getByName("release"))
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += "release"
        }
    }
    flavorDimensions += "source"
    productFlavors {
        create("demo") { dimension = "source"; applicationIdSuffix = ".demo"; buildConfigField("boolean", "DEMO", "true") }
        create("connected") { dimension = "source"; buildConfigField("boolean", "DEMO", "false") }
    }
    buildFeatures { compose = true; buildConfig = true }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    if (providers.gradleProperty("portalProbe").isPresent) {
        sourceSets.getByName("androidTest").java.srcDir("src/portalProbe/kotlin")
    }
}
dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:designsystem"))
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.material3)
    implementation(libs.compose.icons)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.compose)
    implementation(libs.lifecycle.viewmodel)
    implementation(libs.datastore)
    implementation(project(":feature:focus"))
    implementation(project(":feature:college"))
    implementation(project(":focus:data"))
    implementation(project(":college:data"))
    implementation(project(":integration:vtop"))
    implementation(project(":integration:monitoring"))
    implementation(project(":usage:data"))
    implementation(project(":integration:googletasks"))
    implementation(project(":feature:training"))
    implementation(project(":training:data"))
    implementation(libs.work)
    debugImplementation(libs.compose.tooling)
    testImplementation(libs.junit)
    testImplementation(libs.orgjson)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.test)
    androidTestImplementation(libs.android.test.runner)
    androidTestImplementation(libs.android.test.junit)
    debugImplementation(libs.compose.testmanifest)
}
