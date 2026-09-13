plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

group = "com.memento"

kotlin {
    wasmJs {
        browser()
        binaries.executable()
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":memento-domain"))
            implementation(project(":memento-storage-contract"))
            implementation(project(":memento-storage-memory"))
            implementation(project(":memento-platform-contract"))
            implementation(project(":memento-platform-web"))
            implementation(project(":memento-presentation"))
            implementation(project(":memento-ui-compose"))
            implementation(project(":memento-data-portability"))
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(libs.kotlinx.coroutines.core)
        }
    }
}
