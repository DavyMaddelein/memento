import SwiftUI
import MementoShared

/// Hosts the shared Compose UI. `MainViewController()` is the Kotlin entry point exported by the
/// `MementoShared` framework (see `memento-app-ios/.../MainViewController.kt`).
struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

struct ContentView: View {
    var body: some View {
        ComposeView()
            .ignoresSafeArea(.keyboard)
    }
}
