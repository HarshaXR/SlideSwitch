import SwiftUI

enum Tab: Hashable { case connect, settings }

struct RootView: View {
    @AppStorage("appearance") private var appearance: Appearance = .system
    @AppStorage("onboarded") private var onboarded = false

    var body: some View {
        ZStack {
            // Not rendered under the splash: glass composites above sibling content.
            if onboarded {
                MainView()
                    .transition(.scale(scale: 1.04))
            }

            // First-launch splash: an animated colour field with a Liquid Glass card over it.
            if !onboarded {
                AuroraBackground()
                    .ignoresSafeArea()
                    .transition(.opacity)
            }
            GlassEffectContainer {
                if !onboarded {
                    WelcomeCard {
                        withAnimation(.spring(response: 0.6, dampingFraction: 0.85)) { onboarded = true }
                    }
                    .transition(.scale(scale: 0.9))
                    .glassEffectTransition(.materialize)
                }
            }
        }
        .onAppear { appearance.apply() }
        .onChange(of: appearance) { _, new in new.apply() }
    }
}

struct MainView: View {
    @Environment(\.colorScheme) private var scheme
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    // `-initialTab settings` on the command line opens Settings first (handy for screenshots).
    @State private var tab: Tab = UserDefaults.standard.string(forKey: "initialTab") == "settings" ? .settings : .connect

    var body: some View {
        VStack(spacing: 0) {
            HStack(alignment: .top) {
                Wordmark()
                Spacer()
                GlassSegmented(
                    items: [
                        .init(value: Tab.connect, title: "Connect", symbol: "antenna.radiowaves.left.and.right"),
                        .init(value: Tab.settings, title: "Settings", symbol: "gearshape.fill"),
                    ],
                    selection: $tab
                )
                .frame(width: 300)
            }
            .padding(.horizontal, 56)
            .padding(.top, 44)

            ZStack {
                switch tab {
                case .connect:
                    ConnectView()
                        .transition(slide(from: .leading))
                case .settings:
                    SettingsView()
                        .transition(slide(from: .trailing))
                }
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
        .background(Theme.background(scheme).ignoresSafeArea())
    }

    /// Slide + slight scale (transforms only, no opacity on glass ancestors).
    private func slide(from edge: HorizontalEdge) -> AnyTransition {
        guard !reduceMotion else { return .identity }
        let dx: CGFloat = edge == .leading ? -60 : 60
        return .asymmetric(
            insertion: .offset(x: dx).combined(with: .scale(scale: 0.96)),
            removal: .offset(x: -dx).combined(with: .scale(scale: 0.96))
        )
    }
}
