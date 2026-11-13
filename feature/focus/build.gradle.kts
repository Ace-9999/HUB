plugins { alias(libs.plugins.android.library); alias(libs.plugins.kotlin.android); alias(libs.plugins.kotlin.compose) }
android { namespace = "dev.personalhub.feature.focus"; buildFeatures { compose = true } }
dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:designsystem"))
    api(project(":focus:api"))
    implementation(libs.compose.icons)
    implementation(libs.lifecycle.compose)
    implementation(libs.lifecycle.viewmodel)
    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
}
