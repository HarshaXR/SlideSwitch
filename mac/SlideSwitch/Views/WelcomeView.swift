import SwiftUI

/// First-launch card: "Welcome to SlideSwitch for Mac" on Liquid Glass.
struct WelcomeCard: View {
    let onContinue: () -> Void
    @State private var shown = false
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        VStack(spacing: 40) {
            VStack(spacing: 22) {
                Text("Welcome to")
                    .font(.system(size: 30))
                Wordmark(size: 56)
            }
            Button(action: onContinue) {
                Text("Continue")
                    .font(.system(size: 17, weight: .semibold))
                    .frame(width: 260, height: 38)
            }
            .buttonStyle(.glassProminent)
            .controlSize(.extraLarge)
            .tint(Theme.accent)
            .keyboardShortcut(.defaultAction)
        }
        .padding(.horizontal, 150)
        .padding(.vertical, 72)
        .glassEffect(.regular, in: .rect(cornerRadius: 56, style: .continuous))
        .scaleEffect(shown || reduceMotion ? 1 : 0.92)
        .offset(y: shown || reduceMotion ? 0 : 24)
        .onAppear {
            withAnimation(.spring(response: 0.7, dampingFraction: 0.8).delay(0.15)) { shown = true }
        }
    }
}

/// Slowly drifting mesh gradient behind the welcome glass, so the glass has something to refract.
struct AuroraBackground: View {
    @Environment(\.colorScheme) private var scheme
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        TimelineView(.animation(minimumInterval: 1.0 / 30, paused: reduceMotion)) { context in
            let t = reduceMotion ? 0 : context.date.timeIntervalSinceReferenceDate
            MeshGradient(
                width: 3, height: 3,
                points: [
                    [0, 0], [0.5, 0], [1, 0],
                    [0, 0.5], [Float(0.5 + 0.18 * sin(t * 0.35)), Float(0.5 + 0.15 * cos(t * 0.28))], [1, 0.5],
                    [0, 1], [Float(0.5 + 0.12 * cos(t * 0.22)), 1], [1, 1],
                ],
                colors: scheme == .dark ? darkColors : lightColors
            )
        }
    }

    private var lightColors: [Color] {
        [.cyan, .blue, .indigo,
         .mint, Theme.accent, .purple,
         .teal, .pink, .orange]
    }

    private var darkColors: [Color] {
        [Color(red: 0.05, green: 0.10, blue: 0.25), Color(red: 0.10, green: 0.18, blue: 0.45), Color(red: 0.20, green: 0.10, blue: 0.40),
         Color(red: 0.02, green: 0.25, blue: 0.30), Theme.accent, Color(red: 0.35, green: 0.12, blue: 0.45),
         Color(red: 0.05, green: 0.15, blue: 0.20), Color(red: 0.40, green: 0.10, blue: 0.25), Color(red: 0.35, green: 0.18, blue: 0.08)]
    }
}
