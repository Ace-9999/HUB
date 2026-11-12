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
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
    debugImplementation(libs.compose.tooling)
    debugImplementation(project(":integration:vtop"))
    debugImplementation(project(":integration:monitoring"))
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.test)
    androidTestImplementation(libs.android.test.runner)
    androidTestImplementation(libs.android.test.junit)
    debugImplementation(libs.compose.testmanifest)
}
