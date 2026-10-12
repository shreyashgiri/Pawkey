import AVFoundation
import UIKit

/// Key sounds and haptics. "system" is the iPhone's own click; "click", "kitten",
/// "cat" and "duck" are PawKeys' sounds (playing them needs Full Access).
final class Feedback {
    private var players: [String: [AVAudioPlayer]] = [:]
    private var next: [String: Int] = [:]
    private let haptic = UIImpactFeedbackGenerator(style: .light)
    private let prefs = Prefs.shared
    var hasFullAccess = false

    static let styles = ["off", "system", "click", "kitten", "cat", "duck"]
    static func label(_ s: String) -> String {
        switch s {
        case "system": return "iPhone click"; case "click": return "Click"; case "kitten": return "Kitten"
        case "cat": return "Cat"; case "duck": return "Duck"; default: return "Silent"
        }
    }

    init() {
        try? AVAudioSession.sharedInstance().setCategory(.ambient, options: [.mixWithOthers])
        for name in ["click", "kitten", "cat", "duck"] {
            guard let url = Bundle.main.url(forResource: "key_\(name)", withExtension: "wav") else { continue }
            // A few players per sound so fast typing can overlap.
            players[name] = (0..<3).compactMap { _ in
                let p = try? AVAudioPlayer(contentsOf: url)
                p?.enableRate = true
                p?.prepareToPlay()
                return p
            }
        }
    }

    func key(_ code: Int) {
        if prefs.haptics && hasFullAccess { haptic.impactOccurred(intensity: 0.6) }
        play(prefs.sound, code: code)
    }

    func tick() {
        if prefs.haptics && hasFullAccess { haptic.impactOccurred(intensity: 0.35) }
    }

    func preview(_ style: String) { play(style, code: 0) }

    private func play(_ style: String, code: Int) {
        switch style {
        case "off": return
        case "system":
            UIDevice.current.playInputClick()
        default:
            guard hasFullAccess, let list = players[style], !list.isEmpty else { UIDevice.current.playInputClick(); return }
            let i = next[style, default: 0]
            next[style] = (i + 1) % list.count
            let p = list[i]
            let base: Float = code == Code.delete ? 0.88 : (code == Code.space ? 0.93 : (code == Code.enter ? 0.84 : 1))
            p.rate = base * Float.random(in: 0.95...1.05)
            p.currentTime = 0
            p.play()
        }
    }
}

/// The keyboard's root view; adopting this lets UIDevice.playInputClick() work.
final class PawInputView: UIInputView, UIInputViewAudioFeedback {
    var enableInputClicksWhenVisible: Bool { true }
}
