plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    // 分类器接口（CategoryClassifier）定义在 :core:domain；:ml:classifier 作为其实现方向内依赖。
    // 方向与 architecture.md 一致：core 内部只允许向内依赖，:ml 不是 core 模块，可依赖 core:domain。
    implementation(project(":core:domain"))
    implementation(libs.kotlinx.serialization.json)
    // 训练按 ARCHITECTURE §7 在 Dispatchers.Default 上跑，故显式声明协程核心（工程既有依赖，非新增第三方库）。
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlinx.coroutines.test)
}
