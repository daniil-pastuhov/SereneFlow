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
            .onOpenURL { url in
                if url.isFileURL {
                    // A .sereneflow routine file opened with the app (Files, Mail, AirDrop).
                    MainViewControllerKt.openRoutineFile(url: url)
                } else {
                    // sereneflow://start?routine=<id>, from the widget or a link.
                    MainViewControllerKt.openAppUrl(url: url.absoluteString)
                }
            }
    }
}
