import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
  id("com.android.library")
  id("org.jetbrains.kotlin.android")
}

group = "dev.nanaloveyuki.wasee"
version = "0.1.0"

android {
  namespace = "dev.nanaloveyuki.wasee.host"
  compileSdk = 36
  buildToolsVersion = "37.0.0"

  defaultConfig {
    minSdk = 26
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
    isCoreLibraryDesugaringEnabled = true
  }
}

kotlin {
  compilerOptions {
    jvmTarget.set(JvmTarget.JVM_17)
  }
}

dependencies {
  implementation("com.dylibso.chicory:runtime:1.7.5")
  implementation("com.dylibso.chicory:wasm:1.7.5")
  implementation("com.dylibso.chicory:wasi:1.7.5")
  coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")
}
