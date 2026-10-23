import SwiftUI
import UIKit
import HealthTracker

@main
struct LumaApp: App {
    var body: some Scene {
        WindowGroup { SharedApp().ignoresSafeArea() }
    }
}

struct SharedApp: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        let baseUrl = Bundle.main.object(forInfoDictionaryKey: "ApiBaseUrl") as! String
        return MainViewControllerKt.MainViewController(baseUrl: baseUrl)
    }
    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}
