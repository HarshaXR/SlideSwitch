import SwiftUI
import AppKit

/// Appearance setting (Settings › Appearance). Applied app-wide through NSApp.appearance,
/// which reliably returns to "follow the system" (SwiftUI's preferredColorScheme(nil) can stick).
enum Appearance: String, CaseIterable, Identifiable {
    case system, light, dark
    var id: String { rawValue }

    var title: String {
        switch self {
        case .system: "System"
        case .light: "Light"
        case .dark: "Dark"
        }
    }

    var symbol: String {
        switch self {
        case .system: "circle.lefthalf.filled"
        case .light: "sun.max.fill"
        case .dark: "moon.fill"
        }
    }

    @MainActor
    func apply() {
        switch self {
        case .system: NSApp.appearance = nil
        case .light: NSApp.appearance = NSAppearance(named: .aqua)
        case .dark: NSApp.appearance = NSAppearance(named: .darkAqua)
        }
    }
}

enum Theme {
    /// Same blue as the Android app's primary buttons.
    static let accent = Color(red: 0.184, green: 0.435, blue: 0.749)

    /// Solid window canvas: white, or a deep macOS-style dark grey.
    static func background(_ scheme: ColorScheme) -> Color {
        scheme == .dark ? Color(red: 0.118, green: 0.118, blue: 0.125) : .white
    }

    /// Opaque inner panel (QR / pairing details) inside the glass card.
    static func panel(_ scheme: ColorScheme) -> Color {
        scheme == .dark ? Color(red: 0.19, green: 0.19, blue: 0.20) : .white
    }

    /// Subtle fill for selection pills and digit tiles (never glass-on-glass).
    static func softFill(_ scheme: ColorScheme) -> Color {
        scheme == .dark ? Color.white.opacity(0.14) : Color.black.opacity(0.08)
    }

    /// Card glass: on a plain white/dark canvas regular glass nearly vanishes, so it gets a soft
    /// neutral tint (the grey cards from the design) while keeping the glass material and edges.
    static func cardGlass(_ scheme: ColorScheme) -> Glass {
        .regular.tint(scheme == .dark ? Color.white.opacity(0.06) : Color.black.opacity(0.07))
    }

    static let hero = Font.system(size: 44, weight: .bold)
    static let title = Font.system(size: 40, weight: .bold)
    static let subtitle = Font.system(size: 20, weight: .regular)

    static func spring(_ reduceMotion: Bool) -> Animation? {
        reduceMotion ? nil : .spring(response: 0.42, dampingFraction: 0.8)
    }
}

/// "SlideSwitch / For Mac" wordmark.
struct Wordmark: View {
    var size: CGFloat = 44
    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            Text("SlideSwitch")
                .font(.system(size: size, weight: .bold))
            Text("For Mac")
                .font(.system(size: size * 0.45))
        }
    }
}

/// Capsule segmented control on Liquid Glass with a springy sliding selection pill.
/// The pill is a plain fill (not glass) so there is only one glass layer.
struct GlassSegmented<Value: Hashable>: View {
    struct Item: Identifiable {
        let value: Value
        let title: String
        let symbol: String
        var id: String { title }
    }

    let items: [Item]
    @Binding var selection: Value
    var height: CGFloat = 46

    @Namespace private var pill
    @Environment(\.colorScheme) private var scheme
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        HStack(spacing: 0) {
            ForEach(items) { item in
                let selected = item.value == selection
                Button {
                    withAnimation(Theme.spring(reduceMotion)) { selection = item.value }
                } label: {
                    HStack(spacing: 7) {
                        Image(systemName: item.symbol)
                            .symbolEffect(.bounce, value: selected)
                        Text(item.title)
                    }
                    .font(.system(size: 14, weight: selected ? .semibold : .regular))
                    .frame(maxWidth: .infinity)
                    .frame(height: height - 8)
                    .background {
                        if selected {
                            Capsule()
                                .fill(Theme.softFill(scheme))
                                .matchedGeometryEffect(id: "pill", in: pill)
                        }
                    }
                    .contentShape(.capsule)
                }
                .buttonStyle(.plain)
                .focusEffectDisabled()
            }
        }
        .padding(4)
        .glassEffect(Theme.cardGlass(scheme).interactive(), in: .capsule)
    }
}
