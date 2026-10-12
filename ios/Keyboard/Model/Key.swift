import UIKit

enum KeyType { case char, function, action, space }

enum Code {
    static let shift = -1, symbols = -2, alpha = -3, symbolsMore = -4, delete = -5, emoji = -6, globe = -7
    static let enter = 10, space = 32
}

/// One key. [code] is the character for printable keys, or a negative [Code] for special keys.
final class Key {
    let code: Int
    let label: String
    let hint: String?
    let width: CGFloat
    let type: KeyType
    let symbol: String?
    let output: String?
    let popupChars: [String]
    /// Position in the keyboard view, set during layout.
    var frame: CGRect = .zero

    init(_ code: Int, _ label: String, hint: String? = nil, popup: String = "", width: CGFloat = 1, type: KeyType = .char,
         symbol: String? = nil, output: String? = nil) {
        self.code = code; self.label = label; self.hint = hint; self.width = width; self.type = type
        self.symbol = symbol; self.output = output
        var p: [String] = []
        if let h = hint { p.append(h) }
        p += popup.split(separator: " ").map(String.init).filter { $0 != hint }
        popupChars = p
    }

    var isLetter: Bool { code > 0 && (Unicode.Scalar(code).map { CharacterSet.letters.contains($0) } ?? false) }
    var text: String { output ?? (code > 0 ? String(Character(Unicode.Scalar(code)!)) : "") }
}

enum LayoutId { case qwerty, symbols, symbolsMore, numpad, phone }
enum FieldVariant { case text, email, url }

struct KeyboardLayout {
    let id: LayoutId
    let rows: [[Key]]
}

/// Builds every layout, adapting labels and the bottom row to the breed.
enum Layouts {
    private static let accents: [Character: String] = [
        "a": "à á â ä æ ã å ā", "c": "ç ć č", "e": "è é ê ë ē ė ę", "g": "ğ", "i": "î ï í ī į ì", "l": "ł",
        "n": "ñ ń", "o": "ô ö ò ó œ ø ō õ", "s": "ß ś š", "u": "û ü ù ú ū", "y": "ÿ ý", "z": "ž ź ż",
    ]
    private static let row1Hints = Array("1234567890").map(String.init)
    private static let row2Hints = ["@", "#", "$", "_", "&", "-", "+", "(", ")"]
    private static let row3Hints = ["*", "\"", "'", ":", ";", "!", "?"]

    private static func letter(_ c: Character, _ hint: String?) -> Key {
        Key(Int(c.unicodeScalars.first!.value), String(c), hint: hint, popup: accents[c] ?? "")
    }

    static func build(_ id: LayoutId, numberRow: Bool, variant: FieldVariant, breed: Breed, globe: Bool) -> KeyboardLayout {
        switch id {
        case .qwerty: return qwerty(numberRow, variant, breed, globe)
        case .symbols: return symbols(variant, breed, globe)
        case .symbolsMore: return symbolsMore(variant, breed, globe)
        case .numpad: return numpad(phone: false)
        case .phone: return numpad(phone: true)
        }
    }

    private static func qwerty(_ numberRow: Bool, _ v: FieldVariant, _ b: Breed, _ globe: Bool) -> KeyboardLayout {
        var rows: [[Key]] = []
        if numberRow { rows.append(chars("1 2 3 4 5 6 7 8 9 0")) }
        rows.append(Array("qwertyuiop").enumerated().map { letter($1, numberRow ? nil : row1Hints[$0]) })
        rows.append(Array("asdfghjkl").enumerated().map { letter($1, row2Hints[$0]) })
        rows.append([Key(Code.shift, "", width: 1.5, type: .function, symbol: "shift")]
                    + Array("zxcvbnm").enumerated().map { letter($1, row3Hints[$0]) }
                    + [Key(Code.delete, "", width: 1.5, type: .function, symbol: "delete.left")])
        rows.append(bottomRow(b, b.symbolsLabel, Code.symbols, v, globe))
        return KeyboardLayout(id: .qwerty, rows: rows)
    }

    private static func chars(_ s: String, _ popups: [Character: String] = [:]) -> [Key] {
        s.split(separator: " ").map { t in
            let str = String(t)
            return Key(Int(str.unicodeScalars.first!.value), str, popup: popups[str.first!] ?? "")
        }
    }

    private static func bottomRow(_ b: Breed, _ switchLabel: String, _ switchCode: Int, _ v: FieldVariant, _ globe: Bool) -> [Key] {
        let (secondary, secondaryPopup): (String, String) = v == .email ? ("@", ", _ -") : (v == .url ? ("/", ", : _ -") : (",", ""))
        let dotPopup = v == .text ? "? ! ' \" - : ; / @ # …" : ".com .net .org .io - _ : /"
        let sw = { (w: CGFloat) in Key(switchCode, switchLabel, width: w, type: .function) }
        let comma = Key(Int(secondary.unicodeScalars.first!.value), secondary, hint: v == .text ? "☺" : nil, popup: secondaryPopup)
        let dot = Key(46, ".", popup: dotPopup)
        let space = { (w: CGFloat) in Key(Code.space, "", width: w, type: .space) }
        let enter = { (w: CGFloat) in Key(Code.enter, "", width: w, type: .action, symbol: "return") }
        let globeKey = Key(Code.globe, "", type: .function, symbol: "globe")
        var row: [Key]
        switch b.bottomRow {
        case .ios:
            let emoji = Key(Code.emoji, "", width: 1.25, type: .function, symbol: "face.smiling")
            row = v == .text ? [sw(1.25), emoji, space(5), enter(2.5)]
                : [sw(1.25), emoji, space(3), Key(Int(secondary.unicodeScalars.first!.value), secondary, popup: secondaryPopup), dot, enter(2.5)]
        case .gboard:
            row = [sw(1.5), Key(Int(secondary.unicodeScalars.first!.value), secondary, popup: secondaryPopup),
                   Key(Code.emoji, "", type: .function, symbol: "face.smiling"), space(4), dot, enter(1.5)]
        case .standard:
            row = [sw(1.5), comma, space(5), dot, enter(1.5)]
        }
        // iPhones without a system globe button need one on the keyboard (Apple requires it).
        if globe, let i = row.firstIndex(where: { $0.code == Code.space }) {
            let s = row[i]
            row[i] = Key(Code.space, "", width: s.width - 1, type: .space)
            row.insert(globeKey, at: i)
        }
        return row
    }

    private static func symbols(_ v: FieldVariant, _ b: Breed, _ globe: Bool) -> KeyboardLayout {
        KeyboardLayout(id: .symbols, rows: [
            chars("1 2 3 4 5 6 7 8 9 0"),
            chars("+ × ÷ = / _ < > [ ]", ["=": "≠ ≈ ±", "<": "≤ « ‹", ">": "≥ » ›"]),
            chars("! @ # $ % ^ & * ( )", ["$": "€ £ ¥ ₹ ₩ ¢", "%": "‰", "*": "★ †", "!": "¡"]),
            [Key(Code.symbolsMore, b.moreSymbolsLabel, width: 1.5, type: .function)]
                + chars("- ' \" : ; , ?", ["-": "– — ·", "'": "‘ ’ ‚", "\"": "“ ” „", "?": "¿"])
                + [Key(Code.delete, "", width: 1.5, type: .function, symbol: "delete.left")],
            bottomRow(b, "ABC", Code.alpha, v, globe),
        ])
    }

    private static func symbolsMore(_ v: FieldVariant, _ b: Breed, _ globe: Bool) -> KeyboardLayout {
        KeyboardLayout(id: .symbolsMore, rows: [
            chars("` ~ \\ | { } € £ ¥ ₩"),
            chars("° • ○ ● □ ■ ♤ ♡ ◇ ♧"),
            chars("☆ ▪ ¤ 《 》 ¡ ¿ ※ § ¶"),
            [Key(Code.symbols, b.backSymbolsLabel, width: 1.5, type: .function)]
                + chars("© ® ™ ✓ … ¢ 🐾")
                + [Key(Code.delete, "", width: 1.5, type: .function, symbol: "delete.left")],
            bottomRow(b, "ABC", Code.alpha, v, globe),
        ])
    }

    private static func numpad(phone: Bool) -> KeyboardLayout {
        func n(_ c: String, _ popup: String = "", _ hint: String? = nil) -> Key {
            Key(Int(c.unicodeScalars.first!.value), c, hint: hint, popup: popup, width: 1.5)
        }
        return KeyboardLayout(id: phone ? .phone : .numpad, rows: [
            [n("1"), n("2", "", phone ? "ABC" : nil), n("3", "", phone ? "DEF" : nil), Key(45, "-", popup: "+ ( ) /", type: .function)],
            [n("4"), n("5"), n("6"), Key(Code.space, "", type: .function, symbol: "space")],
            [n("7"), n("8"), n("9"), Key(Code.delete, "", type: .function, symbol: "delete.left")],
            [phone ? n("*", "# + ( ) ,") : n(",", "+ - /"), n("0", "+"), phone ? n("#", "* + ;") : n(".", ": ;"),
             Key(Code.enter, "", type: .action, symbol: "return")],
        ])
    }
}
