import UIKit

/// A Breed is a keyboard style: key shape, spacing, labels, bottom row and native
/// colours. Every feature stays PawKeys' own whatever the breed.
enum Breed: String, CaseIterable {
    case pawkeys, samsung, iphone, google

    enum Enter { case accent, function, iosText, pill }
    enum BottomRow { case standard, ios, gboard }

    var label: String {
        switch self { case .pawkeys: return "PawKeys"; case .samsung: return "Samsung"; case .iphone: return "iPhone"; case .google: return "Google" }
    }
    var radius: CGFloat { switch self { case .pawkeys: return 10; case .samsung: return 8; case .iphone: return 5.5; case .google: return 7 } }
    var gapH: CGFloat { self == .samsung ? 2.5 : 3 }
    var gapV: CGFloat { switch self { case .pawkeys: return 5; case .samsung: return 4.5; case .iphone: return 6; case .google: return 5 } }
    var rowHeight: CGFloat { self == .pawkeys ? 54 : 52 }
    var padSide: CGFloat { switch self { case .pawkeys: return 5; case .samsung: return 3; case .iphone: return 3; case .google: return 4 } }
    var padTop: CGFloat { self == .samsung ? 4 : 6 }
    var padBottom: CGFloat { self == .pawkeys ? 8 : 6 }
    var keyShadow: Bool { self == .iphone }
    var enter: Enter { switch self { case .pawkeys: return .accent; case .samsung: return .function; case .iphone: return .iosText; case .google: return .pill } }
    var bottomRow: BottomRow { switch self { case .iphone: return .ios; case .google: return .gboard; default: return .standard } }
    var spaceLabel: String? { switch self { case .pawkeys: return nil; case .samsung: return "English (US)"; case .iphone: return "space"; case .google: return "English" } }
    var symbolsLabel: String { switch self { case .samsung: return "!#1"; case .google: return "?123"; default: return "123" } }
    var moreSymbolsLabel: String { switch self { case .samsung: return "1/2"; case .iphone: return "#+="; default: return "=\\<" } }
    var backSymbolsLabel: String { self == .samsung ? "2/2" : "123" }

    var lightCoat: Coat {
        switch self {
        case .pawkeys: return .calico
        case .samsung: return Coat("samsung_light", "Samsung light", dark: false, bg: 0xF0F1F3, key: 0xFFFFFF, fn: 0xDDDFE4, pressed: 0xC8CBD2,
                                   text: 0x111111, hint: 0x8A8C93, accent: 0x3E91FF, onAccent: 0xFFFFFF, border: UIColor(white: 0, alpha: 0.08), strip: 0x3C3D42, popup: 0xFFFFFF)
        case .iphone: return Coat("ios_light", "iPhone light", dark: false, bg: 0xD1D3D9, key: 0xFFFFFF, fn: 0xABB0BA, pressed: 0xABB0BA,
                                  text: 0x000000, hint: 0x8A8A8E, accent: 0x007AFF, onAccent: 0xFFFFFF, border: UIColor(hex: 0x898A8D), strip: 0x2C2C2E, popup: 0xFFFFFF)
        case .google: return Coat("gboard_light", "Google light", dark: false, bg: 0xE9EEF6, key: 0xFFFFFF, fn: 0xD3DCEC, pressed: 0xC3CEE2,
                                  text: 0x1F1F1F, hint: 0x747775, accent: 0x0B57D0, onAccent: 0xFFFFFF, border: UIColor(hex: 0x1F1F1F, alpha: 0.08), strip: 0x444746, popup: 0xFFFFFF)
        }
    }

    var darkCoat: Coat {
        switch self {
        case .pawkeys: return .tuxedo
        case .samsung: return Coat("samsung_dark", "Samsung dark", dark: true, bg: 0x171717, key: 0x2F2F2F, fn: 0x242424, pressed: 0x454545,
                                   text: 0xF5F5F5, hint: 0x8C8C8C, accent: 0x4E9BFF, onAccent: 0xFFFFFF, border: UIColor(white: 1, alpha: 0.12), strip: 0xD6D6D6, popup: 0x3A3A3A)
        case .iphone: return Coat("ios_dark", "iPhone dark", dark: true, bg: 0x2B2B2D, key: 0x6B6B6D, fn: 0x464648, pressed: 0x464648,
                                  text: 0xFFFFFF, hint: 0xAEAEB2, accent: 0x0A84FF, onAccent: 0xFFFFFF, border: UIColor(hex: 0x1C1C1E), strip: 0xE5E5EA, popup: 0x6B6B6D)
        case .google: return Coat("gboard_dark", "Google dark", dark: true, bg: 0x1B1C1F, key: 0x35373B, fn: 0x2A2C30, pressed: 0x4A4C51,
                                  text: 0xE3E3E3, hint: 0x9AA0A6, accent: 0xA8C7FA, onAccent: 0x062E6F, border: UIColor(white: 1, alpha: 0.12), strip: 0xC4C7C5, popup: 0x3C3E42)
        }
    }
}
