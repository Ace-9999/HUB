// Repository-wide dependency sources; no dynamic dependencies.
pluginManagement {
    repositories { google(); mavenCentral(); gradlePluginPortal() }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories { google(); mavenCentral() }
}
rootProject.name = "PersonalHub"
include(":app", ":core:model", ":core:designsystem")
include(":focus:data")
include(":integration:vtop", ":integration:monitoring")
include(":focus:api", ":feature:focus")
include(":college:api", ":college:data", ":feature:college")
include(":usage:api", ":usage:data")
include(":integration:googletasks")
include(":training:api", ":training:data", ":feature:training")
