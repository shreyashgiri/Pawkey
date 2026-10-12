import UIKit

/// Emoji grid drawn in one view (fast), inside a scroll view.
final class EmojiGrid: UIView {
    var emojis: [String] = [] { didSet { invalidateIntrinsicContentSize(); setNeedsDisplay() } }
    var onPick: ((String) -> Void)?
    var coat = Coat.calico
    private let columns = 8
    private var cell: CGFloat { bounds.width / CGFloat(columns) }

    override init(frame: CGRect) {
        super.init(frame: frame)
        backgroundColor = .clear
        contentMode = .redraw
        addGestureRecognizer(UITapGestureRecognizer(target: self, action: #selector(tap(_:))))
    }

    required init?(coder: NSCoder) { fatalError() }

    override var intrinsicContentSize: CGSize {
        let w = superview?.bounds.width ?? UIScreen.main.bounds.width
        let rows = (emojis.count + columns - 1) / columns
        return CGSize(width: UIView.noIntrinsicMetric, height: emojis.isEmpty ? 80 : CGFloat(rows) * w / CGFloat(columns))
    }

    override func layoutSubviews() {
        super.layoutSubviews()
        invalidateIntrinsicContentSize()
    }

    override func draw(_ rect: CGRect) {
        let c = cell
        if emojis.isEmpty {
            let s = "Emoji you use will curl up here 🐾" as NSString
            let attrs: [NSAttributedString.Key: Any] = [.font: UIFont.systemFont(ofSize: 14), .foregroundColor: coat.hintText]
            let sz = s.size(withAttributes: attrs)
            s.draw(at: CGPoint(x: (bounds.width - sz.width) / 2, y: 30), withAttributes: attrs)
            return
        }
        let attrs: [NSAttributedString.Key: Any] = [.font: UIFont.systemFont(ofSize: c * 0.58)]
        for (i, e) in emojis.enumerated() {
            let x = CGFloat(i % columns) * c, y = CGFloat(i / columns) * c
            let r = CGRect(x: x, y: y, width: c, height: c)
            guard r.intersects(rect) else { continue }
            let sz = (e as NSString).size(withAttributes: attrs)
            (e as NSString).draw(at: CGPoint(x: x + (c - sz.width) / 2, y: y + (c - sz.height) / 2), withAttributes: attrs)
        }
    }

    @objc private func tap(_ g: UITapGestureRecognizer) {
        let p = g.location(in: self)
        let i = Int(p.y / cell) * columns + min(columns - 1, Int(p.x / cell))
        if i >= 0 && i < emojis.count { onPick?(emojis[i]) }
    }
}

/// Emoji: grid on top; ABC, search, categories and backspace along the bottom.
final class EmojiPanel: UIView {
    private let coat: Coat
    private let grid = EmojiGrid()
    private let scroll = UIScrollView()
    private let title = UILabel()
    private var tabs: [UIButton] = []
    private let onEmoji: (String) -> Void

    init(coat: Coat, onEmoji: @escaping (String) -> Void, onDelete: @escaping () -> Void, onBack: @escaping () -> Void, onSearch: @escaping () -> Void) {
        self.coat = coat
        self.onEmoji = onEmoji
        super.init(frame: .zero)
        backgroundColor = coat.background
        title.font = .systemFont(ofSize: 12)
        title.textColor = coat.hintText
        grid.coat = coat
        grid.onPick = { [weak self] e in self?.pick(e) }
        scroll.showsVerticalScrollIndicator = false
        PanelKit.pin(grid, in: scroll)
        grid.widthAnchor.constraint(equalTo: scroll.frameLayoutGuide.widthAnchor).isActive = true

        let bar = UIStackView()
        bar.axis = .horizontal
        bar.alignment = .fill
        bar.spacing = 0
        let abc = PanelKit.button("ABC", coat: coat, size: 13) { onBack() }
        abc.widthAnchor.constraint(equalToConstant: 52).isActive = true
        bar.addArrangedSubview(abc)
        bar.addArrangedSubview(PanelKit.button(nil, symbol: "magnifyingglass", coat: coat, background: coat.background, tint: coat.hintText) { onSearch() })
        let symbols = ["clock"] + EmojiData.categories.map { $0.symbol }
        for (i, s) in symbols.enumerated() {
            let b = PanelKit.button(nil, symbol: s, coat: coat, background: coat.background, tint: coat.hintText) { [weak self] in self?.select(i) }
            tabs.append(b)
            bar.addArrangedSubview(b)
        }
        let del = PanelKit.button(nil, symbol: "delete.left", coat: coat) { onDelete() }
        del.widthAnchor.constraint(equalToConstant: 48).isActive = true
        bar.addArrangedSubview(del)
        bar.distribution = .fill
        for t in tabs { t.widthAnchor.constraint(equalTo: tabs[0].widthAnchor).isActive = true }
        bar.arrangedSubviews[1].widthAnchor.constraint(equalTo: tabs[0].widthAnchor).isActive = true

        let v = UIStackView(arrangedSubviews: [title, scroll, bar])
        v.axis = .vertical
        v.spacing = 2
        bar.heightAnchor.constraint(equalToConstant: 42).isActive = true
        PanelKit.pin(v, in: self, insets: UIEdgeInsets(top: 4, left: 6, bottom: 4, right: 6))
        select(Prefs.shared.emojiRecents.isEmpty ? 1 : 0)
    }

    required init?(coder: NSCoder) { fatalError() }

    private func select(_ i: Int) {
        for (j, t) in tabs.enumerated() { t.configuration?.baseForegroundColor = j == i ? coat.accent : coat.hintText }
        if i == 0 {
            title.text = "  Recently used"
            grid.emojis = Prefs.shared.emojiRecents
        } else {
            title.text = "  " + EmojiData.categories[i - 1].name
            grid.emojis = EmojiData.categories[i - 1].emojis
        }
        scroll.setContentOffset(.zero, animated: false)
    }

    private func pick(_ e: String) {
        onEmoji(e)
        var r = Prefs.shared.emojiRecents
        r.removeAll { $0 == e }
        r.insert(e, at: 0)
        Prefs.shared.emojiRecents = r
    }
}

/// Finds emoji by their Unicode names, country names for flags, and everyday words.
enum EmojiSearch {
    private static let synonyms: [String: String] = [
        "lol": "😂 🤣 😹 😆", "haha": "😂 🤣 😹 😆 😄", "laugh": "😂 🤣 😹 😆", "love": "❤️ 😍 😻 🥰 😘 💕 💖",
        "heart": "❤️ 🧡 💛 💚 💙 💜 🖤 🤍 💕 💖 😻", "kiss": "😘 😗 😙 😚 😽 💋", "sad": "😢 😭 😿 😞 😔 🥺", "cry": "😢 😭 😿",
        "angry": "😠 😡 🤬 😾", "mad": "😠 😡 🤬 😾", "happy": "😀 😃 😄 😊 😸 😺", "smile": "😀 😃 😄 😊 🙂 😺",
        "ok": "👌 👍 🆗", "yes": "👍 ✅ 👌", "no": "👎 ❌ 🙅", "thanks": "🙏 😊 💐", "please": "🙏 🥺", "party": "🥳 🎉 🎊 🎈",
        "birthday": "🎂 🥳 🎉 🎁", "fire": "🔥", "lit": "🔥", "cool": "😎 🆒", "hi": "👋 😊", "hello": "👋 😊", "bye": "👋",
        "wow": "😮 😲 🤩 🙀", "omg": "😱 🙀 😲", "sleep": "😴 💤", "tired": "😴 🥱 😩", "think": "🤔", "clap": "👏", "strong": "💪",
        "money": "💰 💸 💵 🤑", "food": "🍕 🍔 🍟 🍜 🍣", "coffee": "☕", "tea": "🍵 🫖", "beer": "🍺 🍻", "music": "🎵 🎶 🎧 🎸",
        "cat": "🐱 🐈 🐈‍⬛ 😺 😸 😹 😻 😼 😽 🙀 😿 😾 🐾", "kitty": "🐱 😺 🐈", "paw": "🐾", "dog": "🐶 🐕", "good": "👍 👌 💯",
        "sorry": "😔 🙏 🥺", "dead": "💀 ☠️", "eyes": "👀", "star": "⭐ 🌟 ✨", "flower": "🌸 🌹 🌻 🌷 💐",
    ]
    private static var index: [(String, [String])] = []

    static func describe(_ e: String) -> String {
        let scalars = e.unicodeScalars.filter { $0.value != 0xFE0F && $0.value != 0x200D }
        if scalars.count == 2 && scalars.allSatisfy({ (0x1F1E6...0x1F1FF).contains($0.value) }) {
            let code = String(scalars.map { Character(Unicode.Scalar($0.value - 0x1F1E6 + 65)!) })
            return ((Locale(identifier: "en").localizedString(forRegionCode: code) ?? code) + " flag " + code).lowercased()
        }
        return scalars.compactMap { $0.properties.name }.joined(separator: " ").lowercased()
    }

    static func search(_ query: String, limit: Int = 40) -> [String] {
        let words = query.lowercased().split(separator: " ").map(String.init)
        guard !words.isEmpty else { return [] }
        if index.isEmpty {
            var seen = Set<String>()
            index = EmojiData.categories.flatMap { $0.emojis }.filter { seen.insert($0).inserted }
                .map { ($0, describe($0).split(separator: " ").map(String.init)) }
        }
        var out: [String] = []
        if words.count == 1 { for (k, v) in synonyms where k.hasPrefix(words[0]) { for e in v.split(separator: " ") where !out.contains(String(e)) { out.append(String(e)) } } }
        for (e, names) in index where words.allSatisfy({ w in names.contains { $0.hasPrefix(w) } }) {
            if !out.contains(e) { out.append(e) }
            if out.count >= limit { break }
        }
        return Array(out.prefix(limit))
    }
}
