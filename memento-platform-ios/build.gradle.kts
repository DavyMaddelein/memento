plugins {
    alias(libs.plugins.kotlinMultiplatform)
}

group = "com.memento"

kotlin {
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            api(project(":memento-domain"))
            api(project(":memento-platform-contract"))
            implementation(project(":memento-storage-contract"))
            implementation(libs.kotlinx.coroutines.core)
        }
    }
}
