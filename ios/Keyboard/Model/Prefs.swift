import Foundation

/// Settings, stored in the keyboard's own UserDefaults (changed from the Den's Settings panel).
final class Prefs {
    private let d = UserDefaults.standard
    static let shared = Prefs()

    private func bool(_ k: String, _ def: Bool) -> Bool { d.object(forKey: k) == nil ? def : d.bool(forKey: k) }
    private func str(_ k: String, _ def: String) -> String { d.string(forKey: k) ?? def }
    private func int(_ k: String, _ def: Int) -> Int { d.object(forKey: k) == nil ? def : d.integer(forKey: k) }

    var coat: String { get { str("coat", "system") } set { d.set(newValue, forKey: "coat") } }
    var breed: Breed { get { Breed(rawValue: str("breed", "pawkeys")) ?? .pawkeys } set { d.set(newValue.rawValue, forKey: "breed") } }
    var numberRow: Bool { get { bool("numberRow", false) } set { d.set(newValue, forKey: "numberRow") } }
    var hints: Bool { get { bool("hints", false) } set { d.set(newValue, forKey: "hints") } }
    var keyOutlines: Bool { get { bool("keyOutlines", false) } set { d.set(newValue, forKey: "keyOutlines") } }
    /// Stretch: keyboard height in percent.
    var height: Int { get { int("height", 100) } set { d.set(min(140, max(70, newValue)), forKey: "height") } }
    /// Single paw: "off", "left" or "right".
    var singlePaw: String { get { str("singlePaw", "off") } set { d.set(newValue, forKey: "singlePaw") } }

    var hunches: Bool { get { bool("hunches", true) } set { d.set(newValue, forKey: "hunches") } }
    var autoFix: Bool { get { bool("autoFix", true) } set { d.set(newValue, forKey: "autoFix") } }
    var memory: Bool { get { bool("memory", true) } set { d.set(newValue, forKey: "memory") } }
    var nextWord: Bool { get { bool("nextWord", true) } set { d.set(newValue, forKey: "nextWord") } }
    var cleanHunches: Bool { get { bool("cleanHunches", true) } set { d.set(newValue, forKey: "cleanHunches") } }
    var autoCaps: Bool { get { bool("autoCaps", true) } set { d.set(newValue, forKey: "autoCaps") } }
    var doubleSpace: Bool { get { bool("doubleSpace", true) } set { d.set(newValue, forKey: "doubleSpace") } }
    var smartSpacing: Bool { get { bool("smartSpacing", true) } set { d.set(newValue, forKey: "smartSpacing") } }

    /// "flicks", "prowl" or "off".
    var gestures: String { get { str("gestures", "flicks") } set { d.set(newValue, forKey: "gestures") } }
    var scoot: Bool { get { bool("scoot", true) } set { d.set(newValue, forKey: "scoot") } }
    var scratch: Bool { get { bool("scratch", true) } set { d.set(newValue, forKey: "scratch") } }
    var runningCat: Bool { get { bool("runningCat", true) } set { d.set(newValue, forKey: "runningCat") } }

    /// "off", "click", "kitten", "cat", "duck" or "system".
    var sound: String { get { str("sound", "system") } set { d.set(newValue, forKey: "sound") } }
    var haptics: Bool { get { bool("haptics", true) } set { d.set(newValue, forKey: "haptics") } }
    var keyPopup: Bool { get { bool("keyPopup", true) } set { d.set(newValue, forKey: "keyPopup") } }

    var toolbar: [String] {
        get { d.stringArray(forKey: "toolbar") ?? ["emoji", "stash", "groom", "translate", "stickers"] }
        set { d.set(newValue, forKey: "toolbar") }
    }
    var giphyKey: String { get { str("giphyKey", "") } set { d.set(newValue.trimmingCharacters(in: .whitespaces), forKey: "giphyKey") } }
    var translateFrom: String { get { str("translateFrom", "en") } set { d.set(newValue, forKey: "translateFrom") } }
    var translateTo: String { get { str("translateTo", "hi") } set { d.set(newValue, forKey: "translateTo") } }

    var tricks: [String: String] {
        get { (d.dictionary(forKey: "tricks") as? [String: String]) ?? ["omw": "On my way! 🐾", "brb": "Be right back", "ty": "Thank you!", "meow": "😺 meow~"] }
        set { d.set(newValue, forKey: "tricks") }
    }
    var emojiRecents: [String] { get { d.stringArray(forKey: "emojiRecents") ?? [] } set { d.set(Array(newValue.prefix(32)), forKey: "emojiRecents") } }
}
