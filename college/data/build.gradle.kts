plugins { alias(libs.plugins.android.library); alias(libs.plugins.kotlin.android); alias(libs.plugins.ksp) }
android { namespace = "dev.personalhub.college.data"; sourceSets.getByName("androidTest").assets.srcDir("$projectDir/schemas") }
ksp { arg("room.schemaLocation", "$projectDir/schemas") }
dependencies {
    implementation(project(":core:model"))
    api(project(":college:api"))
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    androidTestImplementation(libs.android.test.runner)
    androidTestImplementation(libs.android.test.junit)
    androidTestImplementation(libs.room.testing)
    androidTestImplementation(libs.coroutines.test)
}
