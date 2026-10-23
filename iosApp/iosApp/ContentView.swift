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
            // A .sereneflow routine file opened with the app (Files, Mail, AirDrop).
            .onOpenURL { url in
                if url.isFileURL { MainViewControllerKt.openRoutineFile(url: url) }
            }
    }
}
