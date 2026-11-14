import SwiftUI
import WebKit

/// Test browser: every "New user" tap creates a brand-new, empty,
/// in-memory website data store (cookies, localStorage, IndexedDB, cache)
/// plus a random User-Agent and language, so your app sees a new visitor.
/// MAC address and public IP are NOT changed (see README).
@main
struct FreshBrowserApp: App {
    var body: some Scene { WindowGroup { ContentView() } }
}

final class Browser: NSObject, ObservableObject, WKNavigationDelegate {
    @Published var webView: WKWebView
    @Published var urlText = ""
    @Published var info = ""
    private var userId = ""
    private var language = ""

    private let iosVersions = ["16_6", "17_2", "17_5", "18_0", "18_3", "18_6"]
    private let languages = ["en-US,en;q=0.9", "hr-HR,hr;q=0.9,en;q=0.8",
                             "de-DE,de;q=0.9,en;q=0.7", "en-GB,en;q=0.9",
                             "it-IT,it;q=0.9,en;q=0.6", "sl-SI,sl;q=0.9,en;q=0.8"]

    override init() {
        webView = WKWebView()
        super.init()
        newIdentity(reload: false)
    }

    func newIdentity(reload: Bool) {
        let config = WKWebViewConfiguration()
        config.websiteDataStore = .nonPersistent()   // fresh, empty, memory-only
        let wv = WKWebView(frame: .zero, configuration: config)
        wv.navigationDelegate = self
        wv.allowsBackForwardNavigationGestures = true

        userId = String(UUID().uuidString.prefix(8)).lowercased()
        language = languages.randomElement()!
        let v = iosVersions.randomElement()!
        let device = Bool.random() ? "iPhone; CPU iPhone OS" : "iPad; CPU OS"
        let ua = "Mozilla/5.0 (\(device) \(v) like Mac OS X) AppleWebKit/605.1.15 " +
                 "(KHTML, like Gecko) Version/\(v.split(separator: "_").first!).0 " +
                 "Mobile/15E\(Int.random(in: 100...399)) Safari/604.1"
        wv.customUserAgent = ua
        webView = wv
        info = "Test user #\(userId)  |  \(language)\n\(ua)"

        if reload { load(urlText) }
    }

    func load(_ raw: String) {
        var s = raw.trimmingCharacters(in: .whitespaces)
        guard !s.isEmpty else { return }
        if !s.hasPrefix("http://") && !s.hasPrefix("https://") { s = "https://" + s }
        guard let url = URL(string: s) else { return }
        var req = URLRequest(url: url)
        req.setValue(language, forHTTPHeaderField: "Accept-Language")
        req.setValue(userId, forHTTPHeaderField: "X-Test-User-Id")
        webView.load(req)
    }

    func webView(_ webView: WKWebView, didFinish navigation: WKNavigation!) {
        if let u = webView.url?.absoluteString { urlText = u }
    }
}

struct WebViewHost: UIViewRepresentable {
    let webView: WKWebView
    func makeUIView(context: Context) -> WKWebView { webView }
    func updateUIView(_ uiView: WKWebView, context: Context) {}
}

struct ContentView: View {
    @StateObject private var b = Browser()
    var body: some View {
        VStack(spacing: 4) {
            HStack {
                TextField("https://your-app.test", text: $b.urlText)
                    .textFieldStyle(.roundedBorder)
                    .keyboardType(.URL)
                    .autocapitalization(.none)
                    .disableAutocorrection(true)
                    .onSubmit { b.load(b.urlText) }
                Button("Go") { b.load(b.urlText) }
                Button("New user") { b.newIdentity(reload: true) }
                    .buttonStyle(.borderedProminent)
            }
            .padding(.horizontal, 8)
            Text(b.info).font(.system(size: 10)).foregroundColor(.secondary)
                .frame(maxWidth: .infinity, alignment: .leading).padding(.horizontal, 8)
            WebViewHost(webView: b.webView).id(ObjectIdentifier(b.webView))
        }
    }
}
