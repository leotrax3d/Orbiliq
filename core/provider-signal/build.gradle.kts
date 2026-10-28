plugins { alias(libs.plugins.kotlin.jvm); `java-library` }
kotlin { jvmToolchain(17) }
dependencies {
    api(project(":core:provider-api"))
    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
}
