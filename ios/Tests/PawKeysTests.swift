import UIKit
import XCTest

/// Engine checks (same expectations as the Android EngineTest) and screenshots of
/// the real keyboard views, written to $SCREENSHOT_DIR (or the temp folder).
final class PawKeysTests: XCTestCase {
    static var dict: WordDictionary = {
        let url = Bundle(for: PawKeysTests.self).url(forResource: "words_en", withExtension: "txt")!
        return WordDictionary(text: try! String(contentsOf: url, encoding: .utf8))
    }()

    func testAutoFix() {
        let d = Self.dict
        XCTAssertEqual(d.autocorrect("teh"), "the")
        XCTAssertEqual(d.autocorrect("dont"), "don't")
        XCTAssertEqual(d.autocorrect("Im"), "I'm")
        XCTAssertEqual(d.autocorrect("i"), "I")
        XCTAssertEqual(d.autocorrect("keyboarf"), "keyboard")
        XCTAssertNil(d.autocorrect("cat"))
        XCTAssertNil(d.autocorrect("its"))
        XCTAssertNil(d.autocorrect("NASA"))
    }

    func testCompletions() {
        let s = Self.dict.suggest("hel").map { $0.word }
        XCTAssertTrue(s.contains("hello") || s.contains("help"), "\(s)")
    }

    func testProwlDecoding() {
        var centers: [UInt8: CGPoint] = [:]
        for (i, c) in "qwertyuiop".utf8.enumerated() { centers[c] = CGPoint(x: 50 + i * 100, y: 50) }
        for (i, c) in "asdfghjkl".utf8.enumerated() { centers[c] = CGPoint(x: 100 + i * 100, y: 150) }
        for (i, c) in "zxcvbnm".utf8.enumerated() { centers[c] = CGPoint(x: 200 + i * 100, y: 250) }
        let g = GlideDecoder(dictionary: Self.dict)
        for word in ["hello", "cat", "keyboard", "with", "about"] {
            let pts = word.utf8.map { centers[$0]! }
            var trace: [CGPoint] = []
            for i in 0..<(pts.count - 1) {
                for s in 0..<8 {
                    let t = CGFloat(s) / 8
                    trace.append(CGPoint(x: pts[i].x + (pts[i + 1].x - pts[i].x) * t + CGFloat(s % 3 - 1) * 6,
                                         y: pts[i].y + (pts[i + 1].y - pts[i].y) * t + CGFloat(s % 2) * 8))
                }
            }
            trace.append(pts.last!)
            let result = g.decode(trace, centers: centers, keyWidth: 100)
            XCTAssertTrue(result.prefix(2).contains(word), "\(word) -> \(result)")
        }
    }

    func testEmojiSearch() {
        XCTAssertTrue(EmojiSearch.search("pizza").contains("🍕"))
        XCTAssertTrue(EmojiSearch.search("india").contains("🇮🇳"))
        XCTAssertTrue(EmojiSearch.search("lol").contains("😂"))
    }

    // MARK: Screenshots

    private var outDir: URL {
        let dir = ProcessInfo.processInfo.environment["SCREENSHOT_DIR"].map { URL(fileURLWithPath: $0) }
            ?? FileManager.default.temporaryDirectory.appendingPathComponent("pawkeys-shots")
        try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        return dir
    }

    private func save(_ view: UIView, _ name: String, size: CGSize) {
        view.frame = CGRect(origin: .zero, size: size)
        view.setNeedsLayout()
        view.layoutIfNeeded()
        let img = UIGraphicsImageRenderer(size: size).image { _ in view.drawHierarchy(in: view.bounds, afterScreenUpdates: true) }
        try? img.pngData()?.write(to: outDir.appendingPathComponent("\(name).png"))
    }

    /// The keyboard view plus a Whisker bar on top, the way the keyboard shows them.
    private func keyboard(_ breed: Breed, dark: Bool, _ configure: (KeyboardView, WhiskerBar) -> Void = { _, _ in }) -> UIView {
        let coat = Coat.resolve("system", breed: breed, dark: dark)
        let kv = KeyboardView()
        kv.coat = coat
        kv.breed = breed
        kv.layout = Layouts.build(.qwerty, numberRow: false, variant: .text, breed: breed, globe: false)
        kv.enterIsAction = true
        kv.enterLabel = "send"
        kv.enterSymbol = "paperplane"
        let bar = WhiskerBar()
        bar.coat = coat
        let root = UIView()
        root.backgroundColor = coat.background
        bar.frame = CGRect(x: 0, y: 0, width: 393, height: 48)
        root.addSubview(bar)
        root.addSubview(kv)
        kv.frame = CGRect(x: 0, y: 0, width: 393, height: 48 + kv.keysHeight)
        configure(kv, bar)
        return root
    }

    func testScreenshots() {
        for b in Breed.allCases {
            for dark in [false, true] {
                let v = keyboard(b, dark: dark)
                save(v, "ios_breed_\(b.rawValue)\(dark ? "_dark" : "")", size: CGSize(width: 393, height: 48 + (v.subviews[1] as! KeyboardView).keysHeight))
            }
        }
        let hunches = keyboard(.pawkeys, dark: false) { _, bar in
            bar.show(hunches: [Suggestion(word: "kitten", isCorrection: true), Suggestion(word: "kittem"), Suggestion(word: "kittens")], autoIndex: 0)
        }
        save(hunches, "ios_hunches", size: CGSize(width: 393, height: 270))

        let coat = Coat.calico
        save(DenPanel(coat: coat, onTool: { _ in }, onPinsChanged: {}), "ios_den", size: CGSize(width: 393, height: 230))
        save(StickerPanel(coat: coat, onSticker: { _ in }, onBack: {}), "ios_stickers", size: CGSize(width: 393, height: 230))
        save(EmojiPanel(coat: coat, onEmoji: { _ in }, onDelete: {}, onBack: {}, onSearch: {}), "ios_emoji", size: CGSize(width: 393, height: 230))
        save(GroomPanel(coat: coat, action: { _ in }), "ios_groom", size: CGSize(width: 393, height: 230))
        let tb = TranslateBar(coat: coat, onPick: { _ in }, onSwap: {}, onInsert: {}, onClose: {})
        tb.setLanguages("en", "hi")
        tb.show(source: "Good morning, how are you?", result: "सुप्रभात, आप कैसे हैं?", status: nil)
        save(tb, "ios_translate_bar", size: CGSize(width: 393, height: 96))

        // Full sticker pack at send size.
        let sheet = UIGraphicsImageRenderer(size: CGSize(width: 1024, height: 768)).image { ctx in
            UIColor(hex: 0xE9EAEE).setFill()
            ctx.fill(CGRect(x: 0, y: 0, width: 1024, height: 768))
            for (i, st) in StickerArt.pack.enumerated() {
                StickerArt.render(st, size: 256).draw(at: CGPoint(x: (i % 4) * 256, y: (i / 4) * 256))
            }
        }
        try? sheet.pngData()?.write(to: outDir.appendingPathComponent("ios_sticker_pack.png"))
        print("Screenshots in \(outDir.path)")
    }
}
