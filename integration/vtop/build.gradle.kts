plugins { alias(libs.plugins.android.library); alias(libs.plugins.kotlin.android) }
android { namespace = "dev.personalhub.vtop" }
dependencies {
    api(project(":college:api"))
    testImplementation(libs.junit)
    testImplementation(libs.orgjson)
}
