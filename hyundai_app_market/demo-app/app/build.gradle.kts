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
    versionCode = 2
    versionName = "0.2.0-demo"
  }
  compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
  kotlinOptions { jvmTarget = "17" }
  buildFeatures { compose = true; buildConfig = true }
  testOptions {
    unitTests {
      isIncludeAndroidResources = true
      // Robolectric은 그래픽 원시 라이브러리를 임시 폴더에 풀어 불러옵니다. Windows 사용자 폴더 이름에 한글이 있으면
      // 이 경로가 깨져 UnsatisfiedLinkError가 납니다. 영문 경로인 빌드 폴더를 임시 폴더로 씁니다.
      val testTmp = layout.buildDirectory.dir("tmp/unit-test-tmp").get().asFile
      all {
        it.doFirst { testTmp.mkdirs() }
        it.systemProperty("java.io.tmpdir", testTmp.absolutePath)
      }
    }
  }
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
