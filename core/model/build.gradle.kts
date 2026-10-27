plugins { alias(libs.plugins.kotlin.jvm); `java-library` }
kotlin { jvmToolchain(17) }
dependencies {

    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
}
