plugins {
    alias(libs.plugins.kotlinMultiplatform)
}

group = "com.memento"

kotlin {
    wasmJs {
        browser()
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":memento-domain"))
            api(project(":memento-platform-contract"))
            implementation(project(":memento-storage-contract"))
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
        }
        wasmJsMain.dependencies {
            // Required: WebFileInputPhotoPicker and WebBackupInterop rely on kotlinx.browser.document
            // plus the org.w3c.* / org.khronos.webgl.* DOM bindings it provides. Removing it breaks
            // compilation with `Unresolved reference 'browser'/'org'`.
            // Note: 0.3.1 is built against Kotlin/Wasm stdlib 2.1.20, so Gradle prints a
            // non-fatal "stdlib differs from compiler 2.1.0" warning. It is the only version that
            // resolves the required org.w3c.* / kotlinx.browser bindings and it builds and runs.
            implementation("org.jetbrains.kotlinx:kotlinx-browser:0.3.1")
        }
    }
}
