plugins { alias(libs.plugins.android.library); alias(libs.plugins.kotlin.android) }
android { namespace = "dev.personalhub.vtop" }
dependencies { testImplementation(libs.junit) }
