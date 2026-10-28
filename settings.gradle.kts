pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories { google(); mavenCentral() }
}
rootProject.name = "Orbiliq"
include(":app", ":core:model", ":core:domain", ":core:provider-api", ":core:data", ":core:designsystem", ":feature:inbox")

include(":core:provider-mock")
include(":core:provider-signal")
