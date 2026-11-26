// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false

    // Room Database साठी KSP प्लगइन
    id("com.google.devtools.ksp") version "2.0.21-1.0.27" apply false
    alias(libs.plugins.kotlin.android) apply false

    // Firebase Google Services Plugin (नवीन जोडले आहे)
    id("com.google.gms.google-services") version "4.4.1" apply false
}