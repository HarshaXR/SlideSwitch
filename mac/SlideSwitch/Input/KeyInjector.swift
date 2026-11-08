import AppKit
import ApplicationServices
import Carbon.HIToolbox

/// Turns protocol actions into keystrokes for the frontmost app (via CGEvent).
/// Posting synthetic events requires the Accessibility permission.
enum KeyInjector {
    struct Stroke {
        let key: CGKeyCode
        var flags: CGEventFlags = []
    }

    /// Whether this process may post keyboard events.
    static var isTrusted: Bool { AXIsProcessTrusted() }

    /// Shows the system "allow accessibility" prompt (once per launch at most, per macOS rules).
    @discardableResult
    static func requestTrust() -> Bool {
        let options = ["AXTrustedCheckOptionPrompt": true] as CFDictionary
        return AXIsProcessTrustedWithOptions(options)
    }

    static func openAccessibilitySettings() {
        if let url = URL(string: "x-apple.systempreferences:com.apple.preference.security?Privacy_Accessibility") {
            NSWorkspace.shared.open(url)
        }
    }

    /// macOS 27 renamed the Accessibility privacy list to "Device Control and Data Access".
    static var permissionPaneName: String {
        if #available(macOS 27, *) { "Device Control and Data Access" } else { "Accessibility" }
    }

    static var frontmostAppName: String? {
        NSWorkspace.shared.frontmostApplication?.localizedName
    }

    /// Keystroke for an action given the frontmost app, or nil if the action is unknown.
    static func stroke(for action: String) -> Stroke? {
        switch action {
        case "next": return Stroke(key: CGKeyCode(kVK_RightArrow))
        case "previous": return Stroke(key: CGKeyCode(kVK_LeftArrow))
        case "end": return Stroke(key: CGKeyCode(kVK_Escape))
        case "black": return Stroke(key: CGKeyCode(kVK_ANSI_B))
        case "start":
            let bundle = NSWorkspace.shared.frontmostApplication?.bundleIdentifier?.lowercased() ?? ""
            if bundle.contains("keynote") {
                return Stroke(key: CGKeyCode(kVK_ANSI_P), flags: [.maskCommand, .maskAlternate])     // ⌥⌘P
            } else if bundle.contains("powerpoint") {
                return Stroke(key: CGKeyCode(kVK_Return), flags: [.maskCommand, .maskShift])         // ⇧⌘↩ from start
            } else {
                return Stroke(key: CGKeyCode(kVK_Return), flags: [.maskCommand])                     // ⌘↩ e.g. Google Slides
            }
        default:
            return nil
        }
    }

    static func post(_ stroke: Stroke) {
        let source = CGEventSource(stateID: .hidSystemState)
        guard let down = CGEvent(keyboardEventSource: source, virtualKey: stroke.key, keyDown: true),
              let up = CGEvent(keyboardEventSource: source, virtualKey: stroke.key, keyDown: false) else { return }
        down.flags = stroke.flags
        up.flags = stroke.flags
        down.post(tap: .cghidEventTap)
        up.post(tap: .cghidEventTap)
    }
}
