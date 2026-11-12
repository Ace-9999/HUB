plugins { alias(libs.plugins.android.library); alias(libs.plugins.kotlin.android); alias(libs.plugins.kotlin.compose) }
android { namespace = "dev.personalhub.design"; buildFeatures { compose = true } }
dependencies {
    implementation(project(":core:model"))
    api(platform(libs.compose.bom))
    api(libs.compose.material3)
    api(libs.compose.foundation)
    api(libs.compose.ui)
}
