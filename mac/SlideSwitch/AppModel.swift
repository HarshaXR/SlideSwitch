import SwiftUI
import Observation

@MainActor
@Observable
final class AppModel {
    struct LogEntry: Identifiable {
        let id = UUID()
        let date = Date()
        let text: String
        let symbol: String
    }

    private(set) var serverState: RemoteServer.State = .stopped
    private(set) var clients: [RemoteServer.Client] = []
    private(set) var pairingCode: String = AppModel.makeCode()
    private(set) var ipAddress: String? = NetworkInfo.primaryIPv4()
    private(set) var accessibilityTrusted = KeyInjector.isTrusted
    private(set) var log: [LogEntry] = []
    private(set) var pairedDeviceCount = 0

    let serverName: String
    private let server = RemoteServer()
    private var trustTimer: Timer?

    private static let tokensKey = "pairedTokens"       // [token: deviceName]
    private static let serverIdKey = "serverId"

    init() {
        let defaults = UserDefaults.standard
        let serverId = defaults.string(forKey: Self.serverIdKey) ?? {
            let id = UUID().uuidString
            defaults.set(id, forKey: Self.serverIdKey)
            return id
        }()
        serverName = Host.current().localizedName ?? "Mac"
        pairedDeviceCount = (defaults.dictionary(forKey: Self.tokensKey) ?? [:]).count

        server.serverName = serverName
        server.serverId = serverId
        server.onStateChange = { [weak self] state in
            MainActor.assumeIsolated { self?.serverState = state }
        }
        server.onClientsChange = { [weak self] clients in
            MainActor.assumeIsolated { self?.clientsChanged(clients) }
        }
        server.authenticate = { [weak self] code, token, name, deviceId in
            MainActor.assumeIsolated { self?.authenticate(code: code, token: token, name: name, deviceId: deviceId) }
        }
        server.onCommand = { [weak self] action, client in
            MainActor.assumeIsolated { self?.perform(action, from: client) ?? .unsupported }
        }
        server.start()

        // Accessibility trust can change at any time in System Settings; poll lightly.
        trustTimer = Timer.scheduledTimer(withTimeInterval: 2, repeats: true) { [weak self] _ in
            MainActor.assumeIsolated {
                guard let self else { return }
                let trusted = KeyInjector.isTrusted
                if trusted != self.accessibilityTrusted { self.accessibilityTrusted = trusted }
                let ip = NetworkInfo.primaryIPv4()
                if ip != self.ipAddress { self.ipAddress = ip }
            }
        }
    }

    // MARK: Derived

    var port: UInt16? {
        if case .listening(let port) = serverState { return port }
        return nil
    }

    var pairingURL: String? {
        guard let ip = ipAddress, let port else { return nil }
        var components = URLComponents()
        components.scheme = "slideswitch"
        components.host = "pair"
        components.queryItems = [
            .init(name: "host", value: ip),
            .init(name: "port", value: String(port)),
            .init(name: "code", value: pairingCode),
            .init(name: "name", value: serverName),
        ]
        return components.string
    }

    var formattedCode: String {
        "\(pairingCode.prefix(3)) \(pairingCode.suffix(3))"
    }

    // MARK: Actions

    func regenerateCode() {
        pairingCode = Self.makeCode()
        append("New pairing code", symbol: "arrow.clockwise")
    }

    func forgetPairedDevices() {
        UserDefaults.standard.removeObject(forKey: Self.tokensKey)
        pairedDeviceCount = 0
        server.disconnectAll()
        append("Forgot all paired devices", symbol: "trash")
    }

    func requestAccessibility() {
        if !KeyInjector.requestTrust() { KeyInjector.openAccessibilitySettings() }
    }

    func restartServer() { server.start() }

    // MARK: Server callbacks

    private func authenticate(code: String?, token: String?, name: String, deviceId: String) -> String? {
        var tokens = UserDefaults.standard.dictionary(forKey: Self.tokensKey) as? [String: String] ?? [:]
        if let token, tokens[token] != nil {
            tokens[token] = name
            UserDefaults.standard.set(tokens, forKey: Self.tokensKey)
            return token
        }
        guard let code, code.filter(\.isNumber) == pairingCode else {
            append("Rejected pairing from \(name)", symbol: "xmark.shield")
            return nil
        }
        let newToken = UUID().uuidString + UUID().uuidString
        tokens[newToken] = name
        UserDefaults.standard.set(tokens, forKey: Self.tokensKey)
        pairedDeviceCount = tokens.count
        append("Paired with \(name)", symbol: "checkmark.shield")
        return newToken
    }

    private func clientsChanged(_ new: [RemoteServer.Client]) {
        let oldNames = Set(clients.map(\.id))
        for client in new where !oldNames.contains(client.id) {
            append("\(client.deviceName) connected", symbol: "iphone.radiowaves.left.and.right")
        }
        let newIds = Set(new.map(\.id))
        for client in clients where !newIds.contains(client.id) {
            append("\(client.deviceName) disconnected", symbol: "iphone.slash")
        }
        clients = new
    }

    private func perform(_ action: String, from client: RemoteServer.Client) -> RemoteServer.CommandResult {
        guard let stroke = KeyInjector.stroke(for: action) else { return .unsupported }
        guard KeyInjector.isTrusted else {
            accessibilityTrusted = false
            append("Blocked \(action): \(KeyInjector.permissionPaneName) access needed", symbol: "exclamationmark.triangle")
            return .permissionDenied("On the Mac: System Settings › Privacy & Security › \(KeyInjector.permissionPaneName) › turn on SlideSwitch")
        }
        KeyInjector.post(stroke)
        let target = KeyInjector.frontmostAppName.map { " → \($0)" } ?? ""
        append("\(Self.label(for: action))\(target)", symbol: Self.symbol(for: action))
        return .ok
    }

    private func append(_ text: String, symbol: String) {
        log.insert(LogEntry(text: text, symbol: symbol), at: 0)
        if log.count > 50 { log.removeLast(log.count - 50) }
    }

    // MARK: Helpers

    private static func makeCode() -> String {
        String(format: "%06d", Int.random(in: 0...999_999))
    }

    static func label(for action: String) -> String {
        switch action {
        case "next": "Next slide"
        case "previous": "Previous slide"
        case "start": "Start presentation"
        case "end": "End presentation"
        case "black": "Black screen"
        default: action
        }
    }

    static func symbol(for action: String) -> String {
        switch action {
        case "next": "chevron.right"
        case "previous": "chevron.left"
        case "start": "play.fill"
        case "end": "stop.fill"
        case "black": "rectangle.fill"
        default: "questionmark"
        }
    }
}
