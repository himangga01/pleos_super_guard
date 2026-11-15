plugins {
  id("com.android.application")
  id("org.jetbrains.kotlin.android")
  id("org.jetbrains.kotlin.plugin.compose")
}

android {
  namespace = "app.pleossupercare.phone"
  compileSdk = 34
  defaultConfig {
    applicationId = "app.pleossupercare.phone"
    minSdk = 26
    targetSdk = 34
    versionCode = 1
    versionName = "0.1.0-demo"
  }
  compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
  kotlinOptions { jvmTarget = "17" }
  buildFeatures { compose = true; buildConfig = true }
  testOptions {
    unitTests {
      // 분석 계산(FFT · 소리 · 진동 · 경고등 색) 시험은 순수 Kotlin이라 안드로이드 기기 없이 돕니다.
      isReturnDefaultValues = true
    }
  }
}

dependencies {
  val bom = platform("androidx.compose:compose-bom:2024.10.01")
  implementation(bom)
  implementation("androidx.compose.ui:ui")
  implementation("androidx.compose.foundation:foundation")
  implementation("androidx.compose.material3:material3")
  implementation("androidx.activity:activity-compose:1.9.3")
  implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
  // 카메라: 경고등 찍기 · 사진 기록 · 차 화면 QR 읽기(CameraX, Apache-2.0)
  val camerax = "1.3.4"
  implementation("androidx.camera:camera-core:$camerax")
  implementation("androidx.camera:camera-camera2:$camerax")
  implementation("androidx.camera:camera-lifecycle:$camerax")
  implementation("androidx.camera:camera-view:$camerax")
  implementation("com.google.zxing:core:3.5.3") // 차 화면 QR 읽기(차 앱과 같은 판)

  testImplementation("junit:junit:4.13.2")
}
