import SwiftUI

/// Connect tab: one big glass card ("Pair a Device" ⇄ "Pair Manually") plus a glass status strip.
/// Everything glass lives in one GlassEffectContainer so the status capsules can morph.
struct ConnectView: View {
    @Environment(AppModel.self) private var model
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    // `-pairMode manual` on the command line starts on the manual side (handy for screenshots).
    @State private var manual = UserDefaults.standard.string(forKey: "pairMode") == "manual"
    /// True while the user is pairing an additional phone from the connected view.
    @State private var pairingAnother = false
    @Namespace private var glass

    /// With phones connected, show them (unless the user asked to pair another one).
    private var showsPhones: Bool { !model.clients.isEmpty && !pairingAnother }

    var body: some View {
        GlassEffectContainer(spacing: 10) {
            VStack(spacing: 22) {
                // The pairing card and the phones box share one glassEffectID, so the glass
                // morphs between the full-width card and the left-half box.
                if showsPhones {
                    ConnectedPanel(namespace: glass) {
                        withAnimation(Theme.spring(reduceMotion)) { pairingAnother = true }
                    }
                } else {
                    PairCard(
                        manual: $manual,
                        namespace: glass,
                        onBack: model.clients.isEmpty ? nil : {
                            withAnimation(Theme.spring(reduceMotion)) { pairingAnother = false }
                        }
                    )
                }
                StatusStrip(namespace: glass)
            }
            .frame(maxWidth: 980)
        }
        .padding(.horizontal, 56)
        .padding(.top, 26)
        .animation(Theme.spring(reduceMotion), value: model.accessibilityTrusted)
        .animation(Theme.spring(reduceMotion), value: model.clients.count)
        .animation(Theme.spring(reduceMotion), value: showsPhones)
        .onChange(of: model.clients.count) { old, new in
            // A newly paired phone (or the last one leaving) returns to the phones list.
            if new > old || new == 0 { pairingAnother = false }
        }
    }
}

// MARK: - Connected phones

/// Left half: glass box listing connected phones. Right half: a single "Pair Another Device" button.
private struct ConnectedPanel: View {
    let namespace: Namespace.ID
    let onPairAnother: () -> Void
    @Environment(AppModel.self) private var model
    @Environment(\.colorScheme) private var scheme
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        HStack(alignment: .bottom, spacing: 28) {
            VStack(alignment: .leading, spacing: 22) {
                VStack(alignment: .leading, spacing: 8) {
                    Text(model.clients.count == 1 ? "Connected Phone" : "Connected Phones")
                        .font(Theme.title)
                        .contentTransition(.interpolate)
                    Text("Keep your presentation app in front.")
                        .font(Theme.subtitle)
                        .foregroundStyle(.primary.opacity(0.8))
                }
                ScrollView {
                    VStack(spacing: 10) {
                        ForEach(model.clients) { client in
                            PhoneRow(client: client)
                                .transition(reduceMotion ? .opacity : .push(from: .bottom))
                        }
                    }
                }
                .scrollIndicators(.never)
            }
            .padding(34)
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
            .glassEffect(Theme.cardGlass(scheme), in: .rect(cornerRadius: 48, style: .continuous))
            .glassEffectID("main", in: namespace)

            VStack {
                Spacer()
                Button(action: onPairAnother) {
                    Label("Pair Another Device", systemImage: "plus")
                        .font(.system(size: 16, weight: .semibold))
                        .frame(width: 260, height: 36)
                }
                .buttonStyle(.glassProminent)
                .controlSize(.extraLarge)
                .tint(Theme.accent)
                .glassEffectID("pairAnother", in: namespace)
            }
            .frame(maxWidth: .infinity)
            .padding(.bottom, 34)
        }
        .frame(height: 418)
    }
}

private struct PhoneRow: View {
    let client: RemoteServer.Client
    @Environment(\.colorScheme) private var scheme

    var body: some View {
        HStack(spacing: 14) {
            Image(systemName: "iphone.gen3")
                .font(.system(size: 20, weight: .medium))
                .foregroundStyle(.white)
                .frame(width: 44, height: 44)
                .background(
                    LinearGradient(colors: [.cyan, Theme.accent], startPoint: .topLeading, endPoint: .bottomTrailing),
                    in: .rect(cornerRadius: 12, style: .continuous)
                )
            VStack(alignment: .leading, spacing: 2) {
                Text(client.deviceName)
                    .font(.system(size: 17, weight: .semibold))
                    .lineLimit(1)
                Text(client.address)
                    .font(.system(size: 13).monospaced())
                    .foregroundStyle(.secondary)
            }
            Spacer()
            HStack(spacing: 8) {
                PulsingDot(color: .green, pulsing: false)
                Text("Connected")
                    .font(.system(size: 13, weight: .medium))
                    .foregroundStyle(.secondary)
            }
        }
        .padding(12)
        .background(Theme.softFill(scheme), in: .rect(cornerRadius: 18, style: .continuous))
    }
}

// MARK: - Pair card

private struct PairCard: View {
    @Binding var manual: Bool
    let namespace: Namespace.ID
    /// Set when phones are already connected: shows a way back to the phones list.
    var onBack: (() -> Void)?
    @Environment(\.colorScheme) private var scheme
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        HStack(alignment: .top, spacing: 36) {
            VStack(alignment: .leading, spacing: 10) {
                ZStack(alignment: .topLeading) {
                    if manual {
                        heading("Pair Manually", "Enter the following on\nyour device.")
                            .transition(textTransition(forward: true))
                    } else {
                        heading("Pair a Device", "Scan the QR with your\nphone's camera.")
                            .transition(textTransition(forward: false))
                    }
                }
                Spacer(minLength: 24)

                VStack(alignment: .leading, spacing: 12) {
                    if let onBack {
                        Button(action: onBack) {
                            Label("Back to Connected Phones", systemImage: "chevron.left")
                                .font(.system(size: 14, weight: .medium))
                                .frame(width: 230, height: 26)
                        }
                        .buttonStyle(.glass)
                        .controlSize(.large)
                    }
                    Button {
                        withAnimation(Theme.spring(reduceMotion)) { manual.toggle() }
                    } label: {
                        Label(manual ? "Pair with QR" : "Pair Manually",
                              systemImage: manual ? "qrcode" : "keyboard")
                            .font(.system(size: 16, weight: .semibold))
                            .contentTransition(.symbolEffect(.replace))
                            .frame(width: 230, height: 36)
                    }
                    .buttonStyle(.glassProminent)
                    .controlSize(.extraLarge)
                    .tint(Theme.accent)
                }
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
            .padding(.top, 14)

            ZStack {
                if manual {
                    ManualDetails()
                        .transition(panelTransition)
                } else {
                    QRPanel()
                        .transition(panelTransition)
                }
            }
            .frame(width: 400, height: 350)
            .background {
                // QR codes need hard contrast, so the QR side is always white.
                RoundedRectangle(cornerRadius: 34, style: .continuous)
                    .fill(manual ? Theme.panel(scheme) : .white)
            }
            .clipShape(.rect(cornerRadius: 34, style: .continuous))
        }
        .padding(34)
        .frame(height: 418)
        .glassEffect(Theme.cardGlass(scheme), in: .rect(cornerRadius: 48, style: .continuous))
        .glassEffectID("main", in: namespace)
    }

    private func heading(_ title: String, _ subtitle: String) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(title).font(Theme.title)
            Text(subtitle).font(Theme.subtitle).foregroundStyle(.primary.opacity(0.8))
        }
    }

    private func textTransition(forward: Bool) -> AnyTransition {
        reduceMotion ? .opacity : .push(from: forward ? .bottom : .top)
    }

    private var panelTransition: AnyTransition {
        reduceMotion ? .opacity : AnyTransition(.blurReplace).combined(with: .scale(scale: 0.9))
    }
}

// MARK: - QR side

private struct QRPanel: View {
    @Environment(AppModel.self) private var model

    var body: some View {
        Group {
            if let url = model.pairingURL, let image = QRCode.image(for: url) {
                AnimatedQR(image: image)
                    .id(model.pairingCode)        // new code → re-run the reveal
            } else {
                VStack(spacing: 12) {
                    ProgressView()
                    Text("Waiting for the network…")
                        .font(.callout)
                        .foregroundStyle(.secondary)
                }
                .colorScheme(.light)
            }
        }
        .padding(30)
    }
}

/// QR that "grows" out from the centre on appear, then a soft scanner line sweeps over it.
private struct AnimatedQR: View {
    let image: NSImage
    @State private var revealed = false
    @State private var sweep = false
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        Image(nsImage: image)
            .interpolation(.none)
            .resizable()
            .scaledToFit()
            .mask {
                Circle().scaleEffect(revealed ? 1.6 : 0.02)
            }
            .overlay {
                if !reduceMotion {
                    GeometryReader { geo in
                        LinearGradient(
                            colors: [.clear, Theme.accent.opacity(0.35), .clear],
                            startPoint: .top, endPoint: .bottom
                        )
                        .frame(height: 56)
                        .offset(y: sweep ? geo.size.height - 28 : -28)
                        .blendMode(.multiply)
                    }
                    .allowsHitTesting(false)
                }
            }
            .clipped()
            .onAppear {
                withAnimation(reduceMotion ? nil : .spring(response: 0.9, dampingFraction: 0.85)) { revealed = true }
                guard !reduceMotion else { return }
                withAnimation(.easeInOut(duration: 2.4).repeatForever(autoreverses: true).delay(0.8)) { sweep = true }
            }
    }
}

// MARK: - Manual side

private struct ManualDetails: View {
    @Environment(AppModel.self) private var model
    @Environment(\.colorScheme) private var scheme
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var appeared = false

    var body: some View {
        VStack(alignment: .leading, spacing: 22) {
            row("Pairing code", index: 0) {
                HStack(spacing: 8) {
                    ForEach(Array(model.pairingCode.enumerated()), id: \.offset) { i, digit in
                        Text(String(digit))
                            .font(.system(size: 34, weight: .bold, design: .rounded).monospacedDigit())
                            .contentTransition(.numericText())
                            .frame(width: 44, height: 56)
                            .background(Theme.softFill(scheme), in: .rect(cornerRadius: 12, style: .continuous))
                            .offset(y: appeared || reduceMotion ? 0 : 14)
                            .opacity(appeared ? 1 : 0)
                            .animation(Theme.spring(reduceMotion)?.delay(0.12 + Double(i) * 0.05), value: appeared)
                    }
                }
            }
            row("IP address", index: 1) {
                Text(address)
                    .font(.system(size: 24, weight: .semibold, design: .monospaced))
                    .textSelection(.enabled)
                    .contentTransition(.numericText())
            }
            row("Device name", index: 2) {
                Text(model.serverName)
                    .font(.system(size: 22, weight: .semibold))
                    .lineLimit(1)
                    .minimumScaleFactor(0.7)
            }
            Button {
                withAnimation(.snappy) { model.regenerateCode() }
            } label: {
                Label("New code", systemImage: "arrow.clockwise")
                    .font(.system(size: 13, weight: .medium))
            }
            .buttonStyle(.glass)
            // Glass must never get opacity < 1; animate in with a transform instead.
            .scaleEffect(appeared || reduceMotion ? 1 : 0.6, anchor: .leading)
            .animation(Theme.spring(reduceMotion)?.delay(0.35), value: appeared)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .leading)
        .padding(.horizontal, 34)
        .onAppear { appeared = true }
    }

    private var address: String {
        guard let ip = model.ipAddress else { return "No network" }
        return model.port.map { "\(ip):\($0)" } ?? ip
    }

    private func row<Content: View>(_ label: String, index: Int, @ViewBuilder content: () -> Content) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(label.uppercased())
                .font(.system(size: 11, weight: .semibold))
                .tracking(0.6)
                .foregroundStyle(.secondary)
            content()
        }
        .offset(x: appeared || reduceMotion ? 0 : 18)
        .opacity(appeared ? 1 : 0)
        .animation(Theme.spring(reduceMotion)?.delay(Double(index) * 0.08), value: appeared)
    }
}

// MARK: - Status strip

/// Glass capsules under the card: connection state, and (when needed) the Accessibility request.
/// They share the parent container and morph as the permission capsule appears/disappears.
private struct StatusStrip: View {
    let namespace: Namespace.ID
    @Environment(AppModel.self) private var model
    @Environment(\.colorScheme) private var scheme
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        HStack(spacing: 10) {
            HStack(spacing: 10) {
                PulsingDot(color: dotColor, pulsing: model.clients.isEmpty && !reduceMotion)
                Text(statusText)
                    .font(.system(size: 14, weight: .medium))
                    .contentTransition(.interpolate)
            }
            .padding(.horizontal, 18)
            .frame(height: 44)
            .glassEffect(Theme.cardGlass(scheme), in: .capsule)
            .glassEffectID("status", in: namespace)

            if !model.accessibilityTrusted {
                Button {
                    model.requestAccessibility()
                } label: {
                    Label("Grant \(KeyInjector.permissionPaneName)", systemImage: "hand.raised.fill")
                        .font(.system(size: 14, weight: .semibold))
                        .padding(.horizontal, 18)
                        .frame(height: 44)
                        .contentShape(.capsule)
                }
                .buttonStyle(.plain)
                .foregroundStyle(.white)
                .glassEffect(.regular.tint(.orange).interactive(), in: .capsule)
                .glassEffectID("permission", in: namespace)
                .help("Needed to press arrow keys in your presentation app. If it's already on, remove SlideSwitch from the list with − and allow it again.")
            }
            Spacer()
        }
    }

    private var statusText: String {
        switch model.serverState {
        case .listening:
            if let first = model.clients.first {
                model.clients.count == 1 ? "\(first.deviceName) connected" : "\(model.clients.count) phones connected"
            } else {
                "Waiting for a phone"
            }
        case .starting: "Starting…"
        case .stopped: "Server stopped"
        case .failed: "Server error"
        }
    }

    private var dotColor: Color {
        switch model.serverState {
        case .listening: model.clients.isEmpty ? .orange : .green
        case .starting: .yellow
        case .stopped, .failed: .red
        }
    }
}

struct PulsingDot: View {
    let color: Color
    let pulsing: Bool
    @State private var on = false

    var body: some View {
        Circle()
            .fill(color)
            .frame(width: 9, height: 9)
            .background {
                Circle()
                    .stroke(color.opacity(0.6), lineWidth: 2)
                    .scaleEffect(on ? 2.4 : 1)
                    .opacity(on ? 0 : 0.8)
                    .opacity(pulsing ? 1 : 0)
            }
            .onAppear {
                withAnimation(.easeOut(duration: 1.4).repeatForever(autoreverses: false)) { on = true }
            }
    }
}
