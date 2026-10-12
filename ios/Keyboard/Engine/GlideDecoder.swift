import CoreGraphics
import Foundation

/// Prowl (swipe typing): compares the finger trace with the ideal path through each
/// candidate word's keys. Same algorithm as GlideDecoder.kt.
final class GlideDecoder {
    private struct Entry { let word: String; let letters: [UInt8]; let logFreq: Double }
    private var byEnds: [UInt16: [Entry]] = [:]
    private static let n = 40

    init(dictionary: WordDictionary) {
        dictionary.forEachWord { word, key, freq in
            guard key.count >= 2, key.allSatisfy({ $0 >= 97 && $0 <= 122 }) else { return }
            let e = Entry(word: word, letters: GlideDecoder.dedupe(key), logFreq: log(Double(freq)))
            byEnds[GlideDecoder.ends(key.first!, key.last!), default: []].append(e)
        }
    }

    private static func ends(_ a: UInt8, _ b: UInt8) -> UInt16 { UInt16(a) << 8 | UInt16(b) }

    func decode(_ trace: [CGPoint], centers: [UInt8: CGPoint], keyWidth: CGFloat, extra: [String: Int] = [:], limit: Int = 4) -> [String] {
        guard trace.count >= 2, keyWidth > 0 else { return [] }
        let t = GlideDecoder.resample(trace)
        let starts = nearest(trace.first!, centers, keyWidth)
        let finishes = nearest(trace.last!, centers, keyWidth)
        let traceLen = GlideDecoder.length(trace) / keyWidth
        var scored: [(String, Double)] = []

        func consider(_ word: String, _ letters: [UInt8], _ logFreq: Double) {
            var ideal: [CGPoint] = []
            for c in letters { guard let p = centers[c] else { return }; ideal.append(p) }
            let idealLen = GlideDecoder.length(ideal) / keyWidth
            if traceLen < idealLen * 0.45 - 1 || traceLen > idealLen * 2.2 + 1.5 { return }
            let r = GlideDecoder.resample(ideal)
            var sum: CGFloat = 0
            for i in 0..<GlideDecoder.n { sum += hypot(t[i].x - r[i].x, t[i].y - r[i].y) }
            let avg = Double(sum / CGFloat(GlideDecoder.n) / keyWidth)
            scored.append((word, avg * 4 - logFreq * 0.12))
        }

        for s in starts { for e in finishes { byEnds[GlideDecoder.ends(s, e)]?.forEach { consider($0.word, $0.letters, $0.logFreq) } } }
        for (word, freq) in extra {
            let key = WordDictionary.normalize(word)
            if key.count >= 2, let f = key.first, let l = key.last, starts.contains(f), finishes.contains(l), key.allSatisfy({ $0 >= 97 && $0 <= 122 }) {
                consider(word, GlideDecoder.dedupe(key), log(Double(freq)))
            }
        }
        var seen = Set<String>()
        return scored.sorted { $0.1 < $1.1 }.map { $0.0 }.filter { seen.insert($0).inserted }.prefix(limit).map { $0 }
    }

    private func nearest(_ p: CGPoint, _ centers: [UInt8: CGPoint], _ keyWidth: CGFloat) -> Set<UInt8> {
        let byDist = centers.map { ($0.key, hypot($0.value.x - p.x, $0.value.y - p.y)) }.sorted { $0.1 < $1.1 }
        guard let d0 = byDist.first?.1 else { return [] }
        return Set(byDist.filter { $0.1 <= max(d0 * 1.6, keyWidth * 0.9) }.prefix(4).map { $0.0 })
    }

    static func dedupe(_ s: [UInt8]) -> [UInt8] {
        var out: [UInt8] = []
        for c in s where out.last != c { out.append(c) }
        return out
    }

    static func length(_ p: [CGPoint]) -> CGFloat {
        var l: CGFloat = 0
        for i in 1..<max(1, p.count) { l += hypot(p[i].x - p[i - 1].x, p[i].y - p[i - 1].y) }
        return l
    }

    static func resample(_ pts: [CGPoint]) -> [CGPoint] {
        var out = [CGPoint](repeating: pts[0], count: n)
        let total = length(pts)
        if total == 0 || pts.count == 1 { return out }
        let step = total / CGFloat(n - 1)
        var idx = 1, acc: CGFloat = 0, p = pts[0], i = 1
        while i < pts.count && idx < n {
            let seg = hypot(pts[i].x - p.x, pts[i].y - p.y)
            if acc + seg >= step && seg > 0 {
                let t = (step - acc) / seg
                p = CGPoint(x: p.x + t * (pts[i].x - p.x), y: p.y + t * (pts[i].y - p.y))
                out[idx] = p
                idx += 1
                acc = 0
            } else {
                acc += seg
                p = pts[i]
                i += 1
            }
        }
        while idx < n { out[idx] = pts.last!; idx += 1 }
        return out
    }
}
