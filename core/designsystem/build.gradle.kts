plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.compose)
}
android {
    namespace = "org.orbiliq.core.designsystem"
    compileSdk = 37
    buildToolsVersion = "36.0.0"
    defaultConfig {
        minSdk = 26
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    buildFeatures { compose = true }
}
kotlin { jvmToolchain(17) }
dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.material3)
    implementation(libs.compose.ui)
}
