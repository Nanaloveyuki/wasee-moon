import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.gradle.api.tasks.Exec
import org.gradle.api.tasks.testing.Test

plugins {
  id("com.android.application")
  id("org.jetbrains.kotlin.android")
}

val repoRoot = rootProject.projectDir.parentFile
val guestSource = repoRoot.resolve("guest")
val guestArtifact = repoRoot.resolve("_build/wasm/release/build/guest/guest.wasm")
val guestAsset = projectDir.resolve("src/main/assets/guest/guest.wasm")
val guestTestAsset = projectDir.resolve("src/test/resources/guest/guest.wasm")

android {
  namespace = "dev.nanaloveyuki.wasee.demo"
  compileSdk = 36
  buildToolsVersion = "37.0.0"

  defaultConfig {
    applicationId = "dev.nanaloveyuki.wasee.host"
    minSdk = 26
    targetSdk = 36
    versionCode = 1
    versionName = "0.1.0"
  }

  buildTypes {
    release {
      isMinifyEnabled = false
    }
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
  implementation(project(":host"))
  coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")
  testImplementation("junit:junit:4.13.2")
}

val buildGuestWasm = tasks.register<Exec>("buildGuestWasm") {
  inputs.dir(guestSource)
  outputs.files(guestAsset, guestTestAsset)
  workingDir(repoRoot)
  commandLine("moon", "build", "guest", "--target", "wasm", "--release")
  doLast {
    if (!guestArtifact.isFile) {
      throw GradleException("MoonBit guest artifact was not created: " + guestArtifact)
    }
    guestAsset.parentFile.mkdirs()
    guestArtifact.copyTo(guestAsset, overwrite = true)
    guestTestAsset.parentFile.mkdirs()
    guestArtifact.copyTo(guestTestAsset, overwrite = true)
  }
}

tasks.named("preBuild").configure {
  dependsOn(buildGuestWasm)
}

tasks.withType<Test>().configureEach {
  dependsOn(buildGuestWasm)
}
