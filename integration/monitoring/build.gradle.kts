plugins { alias(libs.plugins.android.library); alias(libs.plugins.kotlin.android) }
android { namespace = "dev.personalhub.monitoring" }
dependencies { api(project(":usage:api")); implementation(libs.coroutines); testImplementation(libs.junit) }
