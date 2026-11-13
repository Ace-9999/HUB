plugins { alias(libs.plugins.android.library); alias(libs.plugins.kotlin.android) }
android { namespace = "dev.personalhub.googletasks" }
dependencies {
    api(project(":focus:api"))
    implementation(libs.play.auth)
    implementation(libs.coroutines)
    testImplementation(libs.junit)
    testImplementation(libs.orgjson)
}
