plugins { alias(libs.plugins.android.library); alias(libs.plugins.kotlin.android) }
android { namespace = "dev.personalhub.monitoring" }
dependencies { implementation(libs.coroutines); testImplementation(libs.junit) }
