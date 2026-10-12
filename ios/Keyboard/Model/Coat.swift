import UIKit

extension UIColor {
    convenience init(hex: UInt32, alpha: CGFloat = 1) {
        self.init(red: CGFloat((hex >> 16) & 0xFF) / 255, green: CGFloat((hex >> 8) & 0xFF) / 255,
                  blue: CGFloat(hex & 0xFF) / 255, alpha: alpha)
    }
}

/// A Coat is a colour theme: quiet neutrals and one accent, named after cat coats.
struct Coat {
    let id: String
    let name: String
    let isDark: Bool
    let background: UIColor
    let key: UIColor
    let functionKey: UIColor
    let keyPressed: UIColor
    let keyText: UIColor
    let hintText: UIColor
    let accent: UIColor
    let onAccent: UIColor
    let border: UIColor
    let stripText: UIColor
    let popup: UIColor

    init(_ id: String, _ name: String, dark: Bool, bg: UInt32, key: UInt32, fn: UInt32, pressed: UInt32,
         text: UInt32, hint: UInt32, accent: UInt32, onAccent: UInt32, border: UIColor, strip: UInt32, popup: UInt32) {
        self.id = id; self.name = name; isDark = dark
        background = UIColor(hex: bg); self.key = UIColor(hex: key); functionKey = UIColor(hex: fn)
        keyPressed = UIColor(hex: pressed); keyText = UIColor(hex: text); hintText = UIColor(hex: hint)
        self.accent = UIColor(hex: accent); self.onAccent = UIColor(hex: onAccent); self.border = border
        stripText = UIColor(hex: strip); self.popup = UIColor(hex: popup)
    }

    static let calico = Coat("calico", "Calico", dark: false, bg: 0xF1F2F5, key: 0xFFFFFF, fn: 0xE2E4E9, pressed: 0xD3D6DD,
                             text: 0x1A1B1F, hint: 0x9A9CA5, accent: 0xD9773A, onAccent: 0xFFFFFF, border: UIColor(white: 0, alpha: 0.08), strip: 0x55575E, popup: 0xFFFFFF)
    static let tuxedo = Coat("tuxedo", "Tuxedo", dark: true, bg: 0x111113, key: 0x242428, fn: 0x1A1A1D, pressed: 0x36363C,
                             text: 0xEDEDF0, hint: 0x7D7D86, accent: 0xEFA262, onAccent: 0x1A1208, border: UIColor(white: 1, alpha: 0.12), strip: 0xB9B9C0, popup: 0x2E2E33)
    static let siamese = Coat("siamese", "Siamese", dark: false, bg: 0xF4EFE7, key: 0xFFFDF9, fn: 0xE8DFD1, pressed: 0xDACDB9,
                              text: 0x2E2621, hint: 0xA39383, accent: 0x8A5A3B, onAccent: 0xFFFFFF, border: UIColor(hex: 0x2E2621, alpha: 0.08), strip: 0x5E5047, popup: 0xFFFDF9)
    static let ragdoll = Coat("ragdoll", "Ragdoll", dark: false, bg: 0xEEF1F7, key: 0xFFFFFF, fn: 0xDCE2EE, pressed: 0xC9D2E2,
                              text: 0x18202E, hint: 0x8E97A8, accent: 0x4A78E0, onAccent: 0xFFFFFF, border: UIColor(hex: 0x18202E, alpha: 0.08), strip: 0x4A5366, popup: 0xFFFFFF)
    static let panther = Coat("panther", "Panther", dark: true, bg: 0x000000, key: 0x141416, fn: 0x0A0A0B, pressed: 0x27272B,
                              text: 0xE8E8EA, hint: 0x6E6E75, accent: 0xC6A6FF, onAccent: 0x0E0718, border: UIColor(white: 1, alpha: 0.15), strip: 0xA9A9B0, popup: 0x222226)
    static let contrast = Coat("contrast", "High contrast", dark: true, bg: 0x000000, key: 0x000000, fn: 0x000000, pressed: 0x333300,
                               text: 0xFFE600, hint: 0xFFFFFF, accent: 0xFFE600, onAccent: 0x000000, border: UIColor(hex: 0xFFE600), strip: 0xFFE600, popup: 0x1A1A1A)

    static let all = [calico, tuxedo, siamese, ragdoll, panther, contrast]

    /// "system" (Auto) follows dark mode using the breed's own colours.
    static func resolve(_ id: String, breed: Breed, dark: Bool) -> Coat {
        if id == "system" { return dark ? breed.darkCoat : breed.lightCoat }
        return all.first { $0.id == id } ?? (dark ? breed.darkCoat : breed.lightCoat)
    }
}
