import Foundation

/// Memory: words learned from you, which word follows which, and removed Hunches.
/// Stored as JSON in the keyboard's own container; never leaves the phone.
final class UserDictionary {
    private struct Store: Codable {
        var words: [String: Int] = [:]
        var bigrams: [String: [String: Int]] = [:]
        var blocked: Set<String> = []
    }
    private var store = Store()
    private var dirty = false
    private let url: URL

    init() {
        let dir = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
        try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        url = dir.appendingPathComponent("memory.json")
        if let data = try? Data(contentsOf: url), let s = try? JSONDecoder().decode(Store.self, from: data) { store = s }
    }

    /// Learned words with a pseudo-frequency comparable to the dictionary's.
    var learned: [String: Int] { store.words.filter { $0.value >= 2 }.mapValues { 2000 * $0 } }

    func isBlocked(_ word: String) -> Bool { store.blocked.contains(word.lowercased()) }

    func learnWord(_ word: String, boost: Int = 1) {
        guard word.count >= 2, word.count <= 32 else { return }
        store.blocked.remove(word.lowercased())
        store.words[word, default: 0] += boost
        dirty = true
        if store.words.count > 5000 { store.words = trim(store.words) }
    }

    func learnBigram(_ previous: String, _ next: String) {
        guard !previous.isEmpty, !next.isEmpty else { return }
        let head = previous.lowercased()
        var m = store.bigrams[head] ?? [:]
        m[next, default: 0] += 1
        if m.count > 24 { m = trim(m) }
        store.bigrams[head] = m
        if store.bigrams.count > 3000 { store.bigrams.removeValue(forKey: store.bigrams.keys.first!) }
        dirty = true
    }

    func predictNext(_ previous: String, limit: Int = 3) -> [String] {
        (store.bigrams[previous.lowercased()] ?? [:]).filter { !isBlocked($0.key) }
            .sorted { $0.value > $1.value }.prefix(limit).map { $0.key }
    }

    func block(_ word: String) {
        store.blocked.insert(word.lowercased())
        store.words = store.words.filter { $0.key.lowercased() != word.lowercased() }
        for k in store.bigrams.keys { store.bigrams[k] = store.bigrams[k]?.filter { $0.key.lowercased() != word.lowercased() } }
        dirty = true
    }

    func clear() { store = Store(); dirty = true; save() }

    func save() {
        guard dirty else { return }
        dirty = false
        if let data = try? JSONEncoder().encode(store) { try? data.write(to: url, options: .atomic) }
    }

    private func trim(_ m: [String: Int]) -> [String: Int] {
        Dictionary(uniqueKeysWithValues: m.sorted { $0.value > $1.value }.prefix(m.count * 3 / 4).map { ($0.key, $0.value) })
    }
}

enum Offensive {
    private static let words: Set<String> = [
        "fuck", "fucking", "fucked", "fucker", "fucks", "motherfucker", "shit", "shitty", "bullshit",
        "bitch", "bitches", "cunt", "dick", "dickhead", "cock", "pussy", "asshole", "bastard",
        "whore", "slut", "nigger", "nigga", "faggot", "fag", "retard", "retarded", "twat", "wanker",
    ]
    static func contains(_ w: String) -> Bool { words.contains(w.lowercased()) }
}
