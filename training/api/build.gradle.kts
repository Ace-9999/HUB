plugins { `java-library`; alias(libs.plugins.kotlin.jvm) }
dependencies { api(project(":core:model")); api(libs.coroutines); testImplementation(libs.junit) }
