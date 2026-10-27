plugins { alias(libs.plugins.kotlin.jvm); `java-library` }
kotlin { jvmToolchain(17) }
dependencies {
    api(project(":core:provider-api"))
    api(project(":core:domain"))
    implementation(project(":core:data"))
    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
}
