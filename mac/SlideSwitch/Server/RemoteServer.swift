import Foundation
import Network

/// WebSocket server + Bonjour advertisement for the SlideSwitch protocol (shared/PROTOCOL.md).
/// All callbacks are delivered on the main queue.
final class RemoteServer {
    static let serviceType = "_slideswitch._tcp"
    static let defaultPort: UInt16 = 48620
    static let protocolVersion = 1

    enum State: Equatable {
        case stopped
        case starting
        case listening(port: UInt16)
        case failed(String)
    }

    struct Client: Identifiable, Equatable {
        let id: ObjectIdentifier
        var deviceName: String
        var address: String
    }

    // Callbacks wired up by AppModel.
    var onStateChange: ((State) -> Void)?
    var onClientsChange: (([Client]) -> Void)?
    var onCommand: ((_ action: String, _ client: Client) -> CommandResult)?

    /// Validates a hello. Returns a token on success, nil on failure.
    var authenticate: ((_ code: String?, _ token: String?, _ deviceName: String, _ deviceId: String) -> String?)?

    var serverName: String = Host.current().localizedName ?? "Mac"
    var serverId: String = UUID().uuidString

    private var listener: NWListener?
    private var sessions: [ObjectIdentifier: Session] = [:]
    private var failures: [String: (count: Int, lockedUntil: Date)] = [:]
    private let queue = DispatchQueue.main

    enum CommandResult {
        case ok
        case unsupported
        case permissionDenied(String)
    }

    static let capabilities = ["next", "previous", "start", "end", "black"]

    // MARK: Lifecycle

    func start() {
        stop()
        onStateChange?(.starting)
        do {
            try startListener(port: NWEndpoint.Port(rawValue: Self.defaultPort)!)
        } catch {
            // Fall back to any free port; Bonjour + QR carry the real one.
            do { try startListener(port: .any) } catch {
                onStateChange?(.failed(error.localizedDescription))
            }
        }
    }

    func stop() {
        listener?.cancel()
        listener = nil
        for session in sessions.values { session.connection.cancel() }
        sessions.removeAll()
        publishClients()
        onStateChange?(.stopped)
    }

    /// Re-announce with a new name / TXT record.
    func readvertise() {
        listener?.service = makeService()
    }

    private func startListener(port: NWEndpoint.Port) throws {
        let ws = NWProtocolWebSocket.Options()
        ws.autoReplyPing = true
        let params = NWParameters.tcp
        params.allowLocalEndpointReuse = true
        params.includePeerToPeer = false
        params.defaultProtocolStack.applicationProtocols.insert(ws, at: 0)

        let listener = try NWListener(using: params, on: port)
        listener.service = makeService()
        listener.stateUpdateHandler = { [weak self, weak listener] state in
            guard let self, let listener else { return }
            switch state {
            case .ready:
                self.onStateChange?(.listening(port: listener.port?.rawValue ?? 0))
            case .failed(let error):
                listener.cancel()
                if port != .any, case .posix(let code) = error, code == .EADDRINUSE {
                    try? self.startListener(port: .any)
                } else {
                    self.onStateChange?(.failed(error.localizedDescription))
                }
            default:
                break
            }
        }
        listener.newConnectionHandler = { [weak self] connection in
            self?.accept(connection)
        }
        self.listener = listener
        listener.start(queue: queue)
    }

    private func makeService() -> NWListener.Service {
        var txt = NWTXTRecord()
        txt["v"] = String(Self.protocolVersion)
        txt["platform"] = "macos"
        txt["id"] = serverId
        return NWListener.Service(name: serverName, type: Self.serviceType, domain: nil, txtRecord: txt)
    }

    // MARK: Connections

    private final class Session {
        let connection: NWConnection
        var paired = false
        var deviceName = "Phone"
        var address: String
        init(connection: NWConnection) {
            self.connection = connection
            if case .hostPort(let host, _) = connection.endpoint {
                var text = "\(host)"
                if let pct = text.firstIndex(of: "%") { text = String(text[..<pct]) } // strip interface scope
                address = text.replacingOccurrences(of: "::ffff:", with: "")
            } else {
                address = "\(connection.endpoint)"
            }
        }
    }

    private func accept(_ connection: NWConnection) {
        let session = Session(connection: connection)
        let key = ObjectIdentifier(connection)
        sessions[key] = session
        connection.stateUpdateHandler = { [weak self] state in
            switch state {
            case .ready:
                self?.receive(on: session)
            case .failed, .cancelled:
                self?.sessions[key] = nil
                self?.publishClients()
            default:
                break
            }
        }
        connection.start(queue: queue)
    }

    private func receive(on session: Session) {
        session.connection.receiveMessage { [weak self] data, context, _, error in
            guard let self else { return }
            if let error {
                NSLog("SlideSwitch: receive error \(error)")
                session.connection.cancel()
                return
            }
            if let meta = context?.protocolMetadata(definition: NWProtocolWebSocket.definition) as? NWProtocolWebSocket.Metadata,
               meta.opcode == .close {
                session.connection.cancel()
                return
            }
            if let data, !data.isEmpty {
                self.handle(data, from: session)
            }
            // Keep reading unless the connection went away.
            if self.sessions[ObjectIdentifier(session.connection)] != nil {
                self.receive(on: session)
            }
        }
    }

    // MARK: Protocol

    private func handle(_ data: Data, from session: Session) {
        guard let object = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
              let type = object["type"] as? String else {
            send(["type": "error", "code": "bad_message", "message": "Expected a JSON object with a type"], to: session)
            return
        }

        switch type {
        case "ping":
            send(["type": "pong"], to: session)

        case "hello":
            handleHello(object, from: session)

        case "command":
            let id = object["id"] as? String
            guard session.paired else {
                send(["type": "error", "id": id as Any, "code": "not_paired", "message": "Send hello first"], to: session, thenClose: true)
                return
            }
            guard let action = object["action"] as? String else {
                send(["type": "error", "id": id as Any, "code": "bad_message", "message": "Missing action"], to: session)
                return
            }
            let client = Client(id: ObjectIdentifier(session.connection), deviceName: session.deviceName, address: session.address)
            switch onCommand?(action, client) ?? .unsupported {
            case .ok:
                send(["type": "ack", "id": id as Any, "ok": true], to: session)
            case .unsupported:
                send(["type": "error", "id": id as Any, "code": "unsupported_action", "message": "Unknown action \(action)"], to: session)
            case .permissionDenied(let message):
                send(["type": "error", "id": id as Any, "code": "permission_denied", "message": message], to: session)
            }

        default:
            if session.paired {
                send(["type": "error", "code": "unsupported_type", "message": "Unknown type \(type)"], to: session)
            } else {
                send(["type": "error", "code": "not_paired", "message": "Send hello first"], to: session, thenClose: true)
            }
        }
    }

    private func handleHello(_ object: [String: Any], from session: Session) {
        let version = object["v"] as? Int ?? 1
        guard version <= Self.protocolVersion else {
            send(["type": "error", "code": "version", "message": "Server speaks v\(Self.protocolVersion)"], to: session, thenClose: true)
            return
        }

        let address = session.address
        if let entry = failures[address], entry.lockedUntil > Date() {
            send(["type": "error", "code": "bad_code", "message": "Too many attempts, try again shortly"], to: session, thenClose: true)
            return
        }

        let deviceName = (object["device"] as? String).flatMap { $0.isEmpty ? nil : $0 } ?? "Phone"
        let deviceId = object["deviceId"] as? String ?? address
        let code = object["code"] as? String
        let token = object["token"] as? String

        guard let issued = authenticate?(code, token, deviceName, deviceId) else {
            var entry = failures[address] ?? (0, .distantPast)
            entry.count += 1
            if entry.count >= 3 { entry = (0, Date().addingTimeInterval(5)) }
            failures[address] = entry
            send(["type": "error", "code": "bad_code", "message": "Wrong pairing code"], to: session, thenClose: true)
            return
        }

        failures[address] = nil
        session.paired = true
        session.deviceName = deviceName
        send([
            "type": "welcome",
            "v": Self.protocolVersion,
            "server": serverName,
            "serverId": serverId,
            "platform": "macos",
            "token": issued,
            "capabilities": Self.capabilities,
        ], to: session)
        publishClients()
    }

    func broadcast(_ message: [String: Any]) {
        for session in sessions.values where session.paired { send(message, to: session) }
    }

    /// Drops every connected phone (used when pairings are forgotten).
    func disconnectAll() {
        for session in sessions.values { session.connection.cancel() }
    }

    private func send(_ message: [String: Any], to session: Session, thenClose: Bool = false) {
        let cleaned = message.filter { !($0.value is NSNull) && !isNilAny($0.value) }
        guard let data = try? JSONSerialization.data(withJSONObject: cleaned) else { return }
        let meta = NWProtocolWebSocket.Metadata(opcode: .text)
        let context = NWConnection.ContentContext(identifier: "text", metadata: [meta])
        session.connection.send(content: data, contentContext: context, isComplete: true, completion: .contentProcessed { _ in
            if thenClose { session.connection.cancel() }
        })
    }

    private func isNilAny(_ value: Any) -> Bool {
        if case Optional<Any>.none = value { return true }
        let mirror = Mirror(reflecting: value)
        return mirror.displayStyle == .optional && mirror.children.isEmpty
    }

    private func publishClients() {
        let clients = sessions.values
            .filter(\.paired)
            .map { Client(id: ObjectIdentifier($0.connection), deviceName: $0.deviceName, address: $0.address) }
            .sorted { $0.deviceName < $1.deviceName }
        onClientsChange?(clients)
    }
}
