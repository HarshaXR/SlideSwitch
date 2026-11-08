import SwiftUI

/// Settings tab: glass section cards in one container (two columns).
struct SettingsView: View {
    @Environment(AppModel.self) private var model
    @AppStorage("appearance") private var appearance: Appearance = .system
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        ScrollView {
            GlassEffectContainer(spacing: 0) {
                HStack(alignment: .top, spacing: 22) {
                    VStack(spacing: 22) {
                        GlassSection("Appearance", symbol: "paintpalette.fill") {
                            GlassSegmented(
                                items: Appearance.allCases.map { .init(value: $0, title: $0.title, symbol: $0.symbol) },
                                selection: $appearance,
                                height: 44
                            )
                            Text("Match macOS, or keep SlideSwitch always light or always dark.")
                                .font(.callout)
                                .foregroundStyle(.secondary)
                        }

                        GlassSection("Permissions", symbol: "hand.raised.fill") {
                            HStack(spacing: 12) {
                                Image(systemName: model.accessibilityTrusted ? "checkmark.circle.fill" : "exclamationmark.triangle.fill")
                                    .font(.title2)
                                    .foregroundStyle(model.accessibilityTrusted ? .green : .orange)
                                    .contentTransition(.symbolEffect(.replace))
                                VStack(alignment: .leading, spacing: 2) {
                                    Text(KeyInjector.permissionPaneName)
                                        .font(.headline)
                                    Text(model.accessibilityTrusted
                                         ? "SlideSwitch can press keys in your presentation app."
                                         : "Needed to press arrow keys in Keynote, PowerPoint and Google Slides.")
                                        .font(.callout)
                                        .foregroundStyle(.secondary)
                                }
                            }
                            if !model.accessibilityTrusted {
                                Button("Open Settings", systemImage: "arrow.up.forward.app") { model.requestAccessibility() }
                                    .buttonStyle(.glassProminent)
                                    .tint(.orange)
                                Text("Already switched on? Rebuilt copies don't match the old entry: remove SlideSwitch with −, reopen it, and allow it again.")
                                    .font(.caption)
                                    .foregroundStyle(.secondary)
                            }
                        }

                        GlassSection("Pairing", symbol: "key.fill") {
                            HStack(spacing: 10) {
                                Button("New Pairing Code", systemImage: "arrow.clockwise") {
                                    withAnimation(.snappy) { model.regenerateCode() }
                                }
                                Button("Restart Server", systemImage: "antenna.radiowaves.left.and.right") { model.restartServer() }
                            }
                            .buttonStyle(.glass)
                            Text("Current code \(model.formattedCode) · port \(model.port.map(String.init) ?? "—")")
                                .font(.callout.monospacedDigit())
                                .foregroundStyle(.secondary)
                                .contentTransition(.numericText())
                        }
                    }

                    VStack(spacing: 22) {
                        GlassSection("Phones", symbol: "iphone.gen3") {
                            HStack {
                                Text(model.pairedDeviceCount == 1 ? "1 paired phone" : "\(model.pairedDeviceCount) paired phones")
                                    .font(.headline)
                                    .contentTransition(.numericText())
                                Spacer()
                                Button("Forget All", systemImage: "trash", role: .destructive) {
                                    withAnimation(Theme.spring(reduceMotion)) { model.forgetPairedDevices() }
                                }
                                .buttonStyle(.glass)
                                .disabled(model.pairedDeviceCount == 0)
                            }
                            if model.clients.isEmpty {
                                Text("No phones connected right now.")
                                    .font(.callout)
                                    .foregroundStyle(.secondary)
                            } else {
                                ForEach(model.clients) { client in
                                    HStack(spacing: 10) {
                                        PulsingDot(color: .green, pulsing: false)
                                        Text(client.deviceName).font(.callout.weight(.medium))
                                        Spacer()
                                        Text(client.address).font(.caption.monospaced()).foregroundStyle(.secondary)
                                    }
                                    .transition(.push(from: .top))
                                }
                            }
                        }

                        GlassSection("Activity", symbol: "clock.arrow.circlepath") {
                            if model.log.isEmpty {
                                Text("Commands from your phone will appear here.")
                                    .font(.callout)
                                    .foregroundStyle(.secondary)
                            } else {
                                VStack(alignment: .leading, spacing: 8) {
                                    ForEach(model.log.prefix(10)) { entry in
                                        HStack(spacing: 10) {
                                            Image(systemName: entry.symbol)
                                                .frame(width: 18)
                                                .foregroundStyle(.secondary)
                                            Text(entry.text).lineLimit(1)
                                            Spacer()
                                            Text(entry.date, style: .time)
                                                .font(.caption.monospacedDigit())
                                                .foregroundStyle(.secondary)
                                        }
                                        .font(.callout)
                                        .transition(.push(from: .top))
                                    }
                                }
                                .animation(Theme.spring(reduceMotion), value: model.log.first?.id)
                            }
                        }
                    }
                }
                .frame(maxWidth: 980)
            }
            .padding(.horizontal, 56)
            .padding(.top, 26)
            .padding(.bottom, 40)
            .frame(maxWidth: .infinity)
        }
        .frame(maxHeight: .infinity)
        .scrollEdgeEffectStyle(.soft, for: .top)
        .animation(Theme.spring(reduceMotion), value: model.accessibilityTrusted)
        .animation(Theme.spring(reduceMotion), value: model.clients.count)
    }
}

/// A titled Liquid Glass section card.
struct GlassSection<Content: View>: View {
    let title: String
    let symbol: String
    @ViewBuilder let content: Content
    @Environment(\.colorScheme) private var scheme

    init(_ title: String, symbol: String, @ViewBuilder content: () -> Content) {
        self.title = title
        self.symbol = symbol
        self.content = content()
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            Label(title, systemImage: symbol)
                .font(.system(size: 13, weight: .semibold))
                .foregroundStyle(.secondary)
            content
        }
        .padding(24)
        .frame(maxWidth: .infinity, alignment: .leading)
        .glassEffect(Theme.cardGlass(scheme), in: .rect(cornerRadius: 30, style: .continuous))
    }
}
