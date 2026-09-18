plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

group = "com.memento"

kotlin {
    listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
        target.binaries.framework {
            baseName = "MementoShared"
            isStatic = true
        }
    }

    sourceSets {
        iosMain.dependencies {
            implementation(project(":memento-domain"))
            implementation(project(":memento-storage-contract"))
            implementation(project(":memento-storage-memory"))
            implementation(project(":memento-storage-sqlite"))
            implementation(project(":memento-platform-contract"))
            implementation(project(":memento-platform-ios"))
            implementation(project(":memento-presentation"))
            implementation(project(":memento-ui-compose"))
            implementation(project(":memento-data-portability"))
            implementation(libs.kotlinx.coroutines.core)

            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.materialIconsExtended)
            implementation(compose.ui)
        }
    }
}
