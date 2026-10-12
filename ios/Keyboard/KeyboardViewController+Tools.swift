import UIKit

extension KeyboardViewController {
    func toolTapped(_ tool: Tool) {
        feedback.tick()
        openTool(tool)
    }

    /// Opens a tool from the Whisker bar or the Den.
    func openTool(_ tool: Tool) {
        switch tool {
        case .hide: dismissKeyboard()
        case .numberRow: prefs.numberRow.toggle(); setLayout(layoutId)
        case .singlePaw: prefs.singlePaw = prefs.singlePaw == "off" ? "right" : "off"; applyLook()
        case .gestures:
            prefs.gestures = ["flicks": "prowl", "prowl": "off"][prefs.gestures] ?? "flicks"
            keyboardView.gestures = prefs.gestures
            toast(["prowl": "Prowl on: swipe to type", "off": "Gestures off"][prefs.gestures] ?? "Flicks on")
        case .haptics: prefs.haptics.toggle(); toast(prefs.haptics ? "Haptics on" : "Haptics off")
        case .sound:
            let styles = Feedback.styles
            let next = styles[((styles.firstIndex(of: prefs.sound) ?? 0) + 1) % styles.count]
            prefs.sound = next
            feedback.preview(next)
            toast("Key sound: \(Feedback.label(next))")
        default:
            if activePanel == tool { closePanel() } else { openPanel(tool) }
        }
    }

    func openPanel(_ tool: Tool) {
        toast("\(tool.label) is coming soon")
    }

    func startStashWatch() {}
    func stopStashWatch() {}
}
