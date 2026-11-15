// 플레오스 슈퍼 케어 모바일(폰 보조 앱). 차 앱(demo-app 루트)과 따로 빌드하는 독립 Gradle 프로젝트입니다.
pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }
dependencyResolutionManagement {
  repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
  repositories { google(); mavenCentral() }
}
rootProject.name = "pleos-super-care-phone"
include(":app")
