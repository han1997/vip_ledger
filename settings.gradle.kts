pluginManagement {
    repositories {
        maven("https://maven.aliyun.com/repository/gradle-plugin")
        maven("https://maven.aliyun.com/repository/google")
        maven("https://maven.aliyun.com/repository/public")
        gradlePluginPortal()
        google()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        maven("https://maven.aliyun.com/repository/google")
        maven("https://maven.aliyun.com/repository/public")
        google()
        mavenCentral()
    }
}

rootProject.name = "vip-ledger"

include(":app")
include(":core:common")
include(":core:model")
include(":core:domain")
include(":core:database")
include(":core:data")
include(":core:designsystem")
include(":feature:ledger")
include(":feature:stats")
include(":feature:ai")
include(":feature:sync")
include(":ml:classifier")
