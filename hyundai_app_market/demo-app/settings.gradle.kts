pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }
dependencyResolutionManagement {
  repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
  repositories {
    google()
    mavenCentral()
    // Pleos Playground SDK 공개 저장소 (공모전 안내 문서의 주소)
    maven { url = uri("https://nexus-playground.pleos.ai/repository/maven-releases/") }
  }
}
rootProject.name = "pleos-guard-demo"
include(":app")
