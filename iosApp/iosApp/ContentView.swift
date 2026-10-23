import SwiftUI
import ComposeApp

struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

struct ContentView: View {
    var body: some View {
        ComposeView()
            .ignoresSafeArea(.all)
            // A .sereneflow routine file opened with the app (Files, Mail, AirDrop), or a
            // sereneflow://start link from the widget. See IncomingURL (QuickActions.swift).
            .onOpenURL { url in IncomingURL.handle(url) }
    }
}
