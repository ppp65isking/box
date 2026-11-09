// Top-level build file. AGP 9.1.0 ships Kotlin 2.2.10 built in — do NOT apply
// the `org.jetbrains.kotlin.android` plugin anywhere (it is incompatible with
// AGP 9.x and the build fails). The Compose Compiler plugin is separate and
// still applied, in the app module, with the bundled Kotlin version.
plugins {
    id("com.android.application") version "9.1.0" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.10" apply false
}
