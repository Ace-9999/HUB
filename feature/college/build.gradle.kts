plugins { alias(libs.plugins.android.library); alias(libs.plugins.kotlin.android); alias(libs.plugins.kotlin.compose) }
android { namespace = "dev.personalhub.feature.college"; buildFeatures { compose = true } }
dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:designsystem"))
    api(project(":college:api"))
    implementation(libs.lifecycle.compose)
    implementation(libs.lifecycle.viewmodel)
    testImplementation(libs.junit)
}
