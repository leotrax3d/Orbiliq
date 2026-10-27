plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}
android {
    namespace = "org.orbiliq.app"
    compileSdk = 37
    buildToolsVersion = "36.0.0"
    defaultConfig {
        minSdk = 26
        applicationId = "org.orbiliq.app"
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    buildFeatures { compose = true }
}
kotlin { jvmToolchain(17) }
dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.material3)
    implementation(libs.compose.ui)
    implementation(project(":core:data"))
    implementation(project(":core:domain"))
    implementation(project(":core:designsystem"))
    implementation(project(":feature:inbox"))
    implementation(libs.activity.compose)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.test)
    androidTestImplementation(libs.test.runner)
    androidTestImplementation(libs.test.junit)
    debugImplementation(libs.compose.test.manifest)
}
