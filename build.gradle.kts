// AGP 9 has built-in Kotlin support, so `org.jetbrains.kotlin.android` is not applied
// anywhere in this build. Only the Kotlin *compiler plugins* (compose, serialization)
// are still declared explicitly.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}
