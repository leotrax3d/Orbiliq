plugins { alias(libs.plugins.kotlin.jvm); `java-library` }
kotlin { jvmToolchain(17) }
dependencies {
    implementation(project(":core:domain"))
    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
}
