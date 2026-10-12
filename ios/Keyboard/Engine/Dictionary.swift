import Foundation

struct Suggestion: Equatable {
    var word: String
    var score: Double = 0
    /// A fix for a typo rather than a completion of what was typed.
    var isCorrection = false
    /// A Tricks expansion, shown with ↪.
    var isShortcut = false
}

/// Word list with frequency-ranked completion and keyboard-proximity typo correction.
/// Same algorithm as the Android app's Dictionary.kt.
final class WordDictionary {
    private var words: [String] = []
    private var freqs: [Int] = []
    /// Lower-case, apostrophe-free form as bytes ("don't" -> "dont").
    private var keys: [[UInt8]] = []
    private var sorted: [Int] = []
    private var byKey: [[UInt8]: Int] = [:]
    private var byLower: [String: Int] = [:]

    init(text: String) {
        for line in text.split(separator: "\n", omittingEmptySubsequences: true) {
            guard let tab = line.firstIndex(of: "\t") else { continue }
            words.append(String(line[..<tab]))
            freqs.append(Int(line[line.index(after: tab)...].trimmingCharacters(in: .whitespaces)) ?? 1)
        }
        keys = words.map { WordDictionary.normalize($0) }
        for i in words.indices {
            if byKey[keys[i]] == nil { byKey[keys[i]] = i }
            let l = words[i].lowercased()
            if byLower[l] == nil { byLower[l] = i }
        }
        sorted = Array(words.indices).sorted { WordDictionary.less(keys[$0], keys[$1]) }
    }

    var count: Int { words.count }

    func contains(_ word: String) -> Bool { byLower[word.lowercased()] != nil }

    func forEachWord(_ body: (String, [UInt8], Int) -> Void) {
        for i in words.indices { body(words[i], keys[i], freqs[i]) }
    }

    func suggest(_ typed: String, limit: Int = 3, extra: [String: Int] = [:], blocked: (String) -> Bool = { _ in false }) -> [Suggestion] {
        if typed.isEmpty { return [] }
        let key = WordDictionary.normalize(typed)
        if key.isEmpty { return [] }
        var best: [String: Suggestion] = [:]

        func offer(_ word: String, _ score: Double, _ correction: Bool) {
            if blocked(word) && word.lowercased() != typed.lowercased() { return }
            let k = word.lowercased()
            if let prev = best[k], prev.score >= score { return }
            best[k] = Suggestion(word: word, score: score, isCorrection: correction)
        }

        if let i = byLower[typed.lowercased()] { offer(words[i], 100 + log(Double(freqs[i])), false) }
        if let i = byKey[key], words[i].lowercased() != typed.lowercased() { offer(words[i], 50 + log(Double(freqs[i])), true) }

        // Completions.
        let (from, to) = prefixRange(key)
        if from < to {
            for p in from..<to {
                let i = sorted[p]
                let extraLen = keys[i].count - key.count
                if extraLen == 0 { continue }
                offer(words[i], log(Double(freqs[i])) - 1.0 - Double(extraLen) * 0.35, false)
            }
        }

        // Corrections.
        if key.count >= 2 {
            let maxDist = key.count <= 3 ? 1.0 : (key.count <= 6 ? 1.6 : 2.2)
            for i in words.indices {
                let k = keys[i]
                if abs(k.count - key.count) > 2 { continue }
                if k.first != key.first && !KeyProximity.adjacent(k.first ?? 0, key.first ?? 0) { continue }
                if k == key { continue }
                let d = KeyProximity.editDistance(key, k, cutoff: maxDist)
                if d <= maxDist { offer(words[i], log(Double(freqs[i])) - d * 4.0, true) }
            }
        }

        for (word, freq) in extra {
            let wk = WordDictionary.normalize(word)
            if wk == key { offer(word, 100 + log(Double(freq)) + 2, false) }
            else if wk.starts(with: key) { offer(word, log(Double(freq)) + 1 - Double(wk.count - key.count) * 0.35, false) }
            else if key.count >= 3 && abs(wk.count - key.count) <= 2 {
                let d = KeyProximity.editDistance(key, wk, cutoff: 1.6)
                if d <= 1.6 { offer(word, log(Double(freq)) + 1 - d * 4.0, true) }
            }
        }

        return best.values.sorted { $0.score > $1.score }.prefix(limit).map {
            var s = $0
            s.word = WordDictionary.matchCase(typed, s.word)
            return s
        }
    }

    /// The word Auto-fix should replace [typed] with, or nil to leave it alone.
    func autocorrect(_ typed: String, extra: [String: Int] = [:], blocked: (String) -> Bool = { _ in false }) -> String? {
        if typed == "i" { return "I" }
        if typed.count < 2 { return nil }
        if contains(typed) || extra.keys.contains(where: { $0.lowercased() == typed.lowercased() }) { return nil }
        if typed.contains(where: { $0.isNumber }) { return nil }
        if typed.count > 1 && typed.allSatisfy({ $0.isUppercase }) { return nil }
        guard let top = suggest(typed, limit: 2, extra: extra, blocked: blocked).first, top.isCorrection else { return nil }
        if top.word.lowercased() == typed.lowercased() { return nil }
        return top.word
    }

    private func prefixRange(_ prefix: [UInt8]) -> (Int, Int) {
        var lo = 0, hi = sorted.count
        while lo < hi {
            let mid = (lo + hi) / 2
            if WordDictionary.less(keys[sorted[mid]], prefix) { lo = mid + 1 } else { hi = mid }
        }
        let start = lo
        hi = sorted.count
        while lo < hi {
            let mid = (lo + hi) / 2
            if keys[sorted[mid]].starts(with: prefix) { lo = mid + 1 } else { hi = mid }
        }
        return (start, lo)
    }

    static func less(_ a: [UInt8], _ b: [UInt8]) -> Bool { a.lexicographicallyPrecedes(b) }

    static func normalize(_ word: String) -> [UInt8] {
        var out: [UInt8] = []
        out.reserveCapacity(word.utf8.count)
        for c in word.lowercased().utf8 where c != UInt8(ascii: "'") { out.append(c) }
        // Curly apostrophe (U+2019 = E2 80 99) is dropped too.
        if let r = out.firstRange(of: [0xE2, 0x80, 0x99]) { out.removeSubrange(r) }
        return out
    }

    static func matchCase(_ typed: String, _ word: String) -> String {
        if typed.count > 1 && typed.allSatisfy({ !$0.isLetter || $0.isUppercase }) { return word.uppercased() }
        if typed.first?.isUppercase == true { return word.prefix(1).uppercased() + word.dropFirst() }
        return word
    }
}

/// QWERTY geometry: hitting a neighbouring key counts as a smaller mistake.
enum KeyProximity {
    private static let pos: [UInt8: (Float, Float)] = {
        var m: [UInt8: (Float, Float)] = [:]
        for (i, c) in "qwertyuiop".utf8.enumerated() { m[c] = (Float(i), 0) }
        for (i, c) in "asdfghjkl".utf8.enumerated() { m[c] = (Float(i) + 0.5, 1) }
        for (i, c) in "zxcvbnm".utf8.enumerated() { m[c] = (Float(i) + 1.5, 2) }
        return m
    }()

    static func adjacent(_ a: UInt8, _ b: UInt8) -> Bool {
        guard let pa = pos[a], let pb = pos[b] else { return false }
        let dx = pa.0 - pb.0, dy = pa.1 - pb.1
        return dx * dx + dy * dy <= 1.6 * 1.6
    }

    static func editDistance(_ a: [UInt8], _ b: [UInt8], cutoff: Double = .greatestFiniteMagnitude) -> Double {
        let n = a.count, m = b.count
        if n == 0 { return Double(m) }
        if m == 0 { return Double(n) }
        var prev2 = [Double](repeating: 0, count: m + 1)
        var prev = (0...m).map(Double.init)
        var cur = [Double](repeating: 0, count: m + 1)
        for i in 1...n {
            cur[0] = Double(i)
            var rowMin = cur[0]
            for j in 1...m {
                let sub: Double = a[i - 1] == b[j - 1] ? 0 : (adjacent(a[i - 1], b[j - 1]) ? 0.55 : 1)
                var v = min(prev[j] + 1, cur[j - 1] + 1)
                v = min(v, prev[j - 1] + sub)
                if i > 1 && j > 1 && a[i - 1] == b[j - 2] && a[i - 2] == b[j - 1] { v = min(v, prev2[j - 2] + 0.7) }
                cur[j] = v
                if v < rowMin { rowMin = v }
            }
            if rowMin > cutoff { return rowMin }
            (prev2, prev, cur) = (prev, cur, prev2)
        }
        return prev[m]
    }
}
