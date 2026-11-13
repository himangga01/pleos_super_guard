plugins {
  id("com.android.application")
  id("org.jetbrains.kotlin.android")
  id("org.jetbrains.kotlin.plugin.compose")
  id("io.github.takahirom.roborazzi")
}

android {
  namespace = "app.pleosguard.demo"
  compileSdk = 34
  useLibrary("android.car") // AAOS CarPropertyManager(차속 PERF_VEHICLE_SPEED)
  defaultConfig {
    applicationId = "app.pleosguard.demo"
    minSdk = 28
    targetSdk = 34
    versionCode = 1
    versionName = "0.1.0-skeleton"
  }
  compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
  kotlinOptions { jvmTarget = "17" }
  buildFeatures { compose = true }
  testOptions { unitTests { isIncludeAndroidResources = true } }
}

dependencies {
  // Pleos Playground SDK 5종 (공모전 공개 SDK)
  implementation("ai.pleos.playground:Vehicle:2.3.2")
  implementation("ai.pleos.playground:NaviHelper:3.0.5")
  implementation("ai.pleos.playground:TextToSpeech:3.3.1")
  implementation("ai.pleos.playground:SpeechToText:3.3.1")
  implementation("ai.pleos.playground:FusedLocation:1.1.3")

  val bom = platform("androidx.compose:compose-bom:2024.10.01")
  implementation(bom)
  implementation("androidx.compose.ui:ui")
  implementation("androidx.compose.material3:material3")
  implementation("androidx.activity:activity-compose:1.9.3")

  testImplementation(bom)
  testImplementation("androidx.compose.ui:ui-test-junit4")
  testImplementation("junit:junit:4.13.2")
  testImplementation("org.robolectric:robolectric:4.13")
  testImplementation("io.github.takahirom.roborazzi:roborazzi:1.32.2")
  testImplementation("io.github.takahirom.roborazzi:roborazzi-compose:1.32.2")
  debugImplementation("androidx.compose.ui:ui-test-manifest")
}
