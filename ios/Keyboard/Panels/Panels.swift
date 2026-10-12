import UIKit

/// Stash: clipboard history. iPhones only let keyboards read the clipboard with Full
/// Access, and show a "pasted from" notice when they do, so PawKeys checks for a new
/// copy only when you open the Stash.
final class StashStore {
    struct Clip: Codable, Equatable { var text: String; var pinned: Bool; var time: Date }
    private let key = "stash"
    private let d = UserDefaults.standard

    var clips: [Clip] {
        get { (d.data(forKey: key)).flatMap { try? JSONDecoder().decode([Clip].self, from: $0) } ?? [] }
        set { d.set(try? JSONEncoder().encode(newValue), forKey: key) }
    }

    func capture() {
        let pb = UIPasteboard.general
        guard pb.changeCount != d.integer(forKey: "stashChange") else { return }
        d.set(pb.changeCount, forKey: "stashChange")
        guard pb.hasStrings, let text = pb.string, !text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty, text.count <= 5000 else { return }
        var list = clips
        let pinned = list.first { $0.text == text }?.pinned ?? false
        list.removeAll { $0.text == text }
        list.insert(Clip(text: text, pinned: pinned, time: Date()), at: 0)
        while list.filter({ !$0.pinned }).count > 30, let i = list.lastIndex(where: { !$0.pinned }) { list.remove(at: i) }
        clips = list
    }

    func togglePin(_ c: Clip) { var l = clips; if let i = l.firstIndex(of: c) { l[i].pinned.toggle() }; clips = l }
    func remove(_ c: Clip) { clips = clips.filter { $0 != c } }
    func clearUnpinned() { clips = clips.filter { $0.pinned } }
}

final class StashPanel: UIView {
    private let coat: Coat
    private let store: StashStore
    private let list = UIStackView()
    private let onPaste: (String) -> Void
    private let fullAccess: Bool

    init(coat: Coat, store: StashStore, fullAccess: Bool, onPaste: @escaping (String) -> Void, onBack: @escaping () -> Void) {
        self.coat = coat
        self.store = store
        self.onPaste = onPaste
        self.fullAccess = fullAccess
        super.init(frame: .zero)
        backgroundColor = coat.background
        list.axis = .vertical
        list.spacing = 8
        let scroll = UIScrollView()
        scroll.showsVerticalScrollIndicator = false
        PanelKit.pin(list, in: scroll)
        list.widthAnchor.constraint(equalTo: scroll.frameLayoutGuide.widthAnchor).isActive = true
        let abc = PanelKit.button("ABC", coat: coat) { onBack() }
        abc.heightAnchor.constraint(equalToConstant: 42).isActive = true
        let v = UIStackView(arrangedSubviews: [
            PanelKit.header("Stash", coat: coat, action: "Clear all") { [weak self] in self?.store.clearUnpinned(); self?.refresh() },
            scroll, abc,
        ])
        v.axis = .vertical
        v.spacing = 4
        PanelKit.pin(v, in: self, insets: UIEdgeInsets(top: 0, left: 10, bottom: 4, right: 10))
        if fullAccess { store.capture() }
        refresh()
    }

    required init?(coder: NSCoder) { fatalError() }

    private func refresh() {
        list.arrangedSubviews.forEach { $0.removeFromSuperview() }
        let clips = store.clips.sorted { $0.pinned && !$1.pinned }
        if clips.isEmpty {
            let l = PanelKit.label(fullAccess ? "Your Stash is empty.\nAnything you copy will be kept here." :
                                    "Turn on Allow Full Access for PawKeys\n(Settings → General → Keyboard) to use the Stash.",
                                   coat: coat, size: 14, weight: .regular, color: coat.hintText)
            l.numberOfLines = 0
            l.textAlignment = .center
            list.addArrangedSubview(l)
            return
        }
        for c in clips {
            let card = UIView()
            card.backgroundColor = coat.key
            card.layer.cornerRadius = 14
            let text = PanelKit.label(c.text, coat: coat, size: 14, weight: .regular)
            text.numberOfLines = 2
            let pin = PanelKit.button(nil, symbol: c.pinned ? "pin.fill" : "pin", coat: coat, background: coat.key, tint: c.pinned ? coat.accent : coat.hintText) { [weak self] in
                self?.store.togglePin(c); self?.refresh()
            }
            let del = PanelKit.button(nil, symbol: "trash", coat: coat, background: coat.key, tint: coat.hintText) { [weak self] in
                self?.store.remove(c); self?.refresh()
            }
            pin.widthAnchor.constraint(equalToConstant: 36).isActive = true
            del.widthAnchor.constraint(equalToConstant: 36).isActive = true
            let row = UIStackView(arrangedSubviews: [text, pin, del])
            row.alignment = .center
            row.spacing = 4
            PanelKit.pin(row, in: card, insets: UIEdgeInsets(top: 10, left: 14, bottom: 10, right: 4))
            card.addGestureRecognizer(UITapGestureRecognizer(target: self, action: #selector(tapCard(_:))))
            card.accessibilityValue = c.text
            list.addArrangedSubview(card)
        }
    }

    @objc private func tapCard(_ g: UITapGestureRecognizer) {
        if let t = g.view?.accessibilityValue { onPaste(t) }
    }
}

/// Groom: cursor movement and clipboard actions. (iPhone keyboards can't select
/// text in other apps, so there's no select mode here.)
final class GroomPanel: UIView {
    init(coat: Coat, action: @escaping (String) -> Void) {
        super.init(frame: .zero)
        backgroundColor = coat.background
        func b(_ title: String?, _ symbol: String?, _ id: String, accent: Bool = false, fn: Bool = false) -> UIView {
            let btn = PanelKit.button(title, symbol: symbol, coat: coat, background: accent ? coat.accent : (fn ? coat.functionKey : coat.key),
                                      tint: accent ? coat.onAccent : coat.keyText, weight: .regular) { action(id) }
            if id == "left" || id == "right" || id == "delete" {
                // Hold to repeat.
                btn.addGestureRecognizer(RepeatPress { action(id) })
            }
            return btn
        }
        let grid = PanelKit.grid([
            b(nil, "arrow.left.to.line", "lineStart"), b(nil, "chevron.left.2", "wordLeft"), b(nil, "chevron.right.2", "wordRight"), b(nil, "arrow.right.to.line", "lineEnd"),
            b(nil, "chevron.left", "left"), b("Copy", nil, "copy", fn: true), b("Cut", nil, "cut", fn: true), b(nil, "chevron.right", "right"),
            b("ABC", nil, "abc", fn: true), b("Paste", nil, "paste", fn: true), b("Delete word", nil, "deleteWord", fn: true), b(nil, "delete.left", "delete", fn: true),
        ], columns: 4, spacing: 8)
        PanelKit.pin(grid, in: self, insets: UIEdgeInsets(top: 8, left: 8, bottom: 8, right: 8))
    }

    required init?(coder: NSCoder) { fatalError() }
}

/// Fires repeatedly while a button is held down.
final class RepeatPress: UILongPressGestureRecognizer {
    private var timer: Timer?
    private let fire: () -> Void

    init(_ fire: @escaping () -> Void) {
        self.fire = fire
        super.init(target: nil, action: nil)
        minimumPressDuration = 0.4
        cancelsTouchesInView = false
        addTarget(self, action: #selector(changed))
    }

    @objc private func changed() {
        switch state {
        case .began: timer = Timer.scheduledTimer(withTimeInterval: 0.07, repeats: true) { [weak self] _ in self?.fire() }
        case .ended, .cancelled, .failed: timer?.invalidate(); timer = nil
        default: break
        }
    }
}

/// Coats and Breeds pickers: little keyboard previews.
final class SwatchPanel: UIView {
    init(title: String, coat: Coat, items: [(id: String, name: String, coat: Coat, radius: CGFloat, pill: Bool, enterAccent: Bool)],
         selected: String, columns: Int, onPick: @escaping (String) -> Void) {
        super.init(frame: .zero)
        backgroundColor = coat.background
        let cards: [UIView] = items.map { it in
            let c = UIControl()
            c.backgroundColor = it.coat.background
            c.layer.cornerRadius = 14
            c.layer.borderWidth = it.id == selected ? 2.5 : 1
            c.layer.borderColor = (it.id == selected ? coat.accent : coat.border).cgColor
            c.accessibilityLabel = it.name
            let keys = UIStackView()
            keys.spacing = 3
            keys.isUserInteractionEnabled = false
            for (i, color) in [it.coat.key, it.coat.key, it.coat.functionKey, it.enterAccent ? it.coat.accent : it.coat.functionKey].enumerated() {
                let k = UIView()
                k.backgroundColor = color
                k.layer.cornerRadius = i == 3 && it.pill ? 9 : it.radius * 0.6
                k.widthAnchor.constraint(equalToConstant: i == 3 ? 22 : 14).isActive = true
                k.heightAnchor.constraint(equalToConstant: 18).isActive = true
                keys.addArrangedSubview(k)
            }
            let name = PanelKit.label(it.name, coat: it.coat, size: 12, weight: .medium)
            name.textAlignment = .center
            let v = UIStackView(arrangedSubviews: [keys, name])
            v.axis = .vertical
            v.alignment = .center
            v.spacing = 6
            v.isUserInteractionEnabled = false
            v.translatesAutoresizingMaskIntoConstraints = false
            c.addSubview(v)
            NSLayoutConstraint.activate([v.centerXAnchor.constraint(equalTo: c.centerXAnchor), v.centerYAnchor.constraint(equalTo: c.centerYAnchor)])
            c.addAction(UIAction { _ in onPick(it.id) }, for: .touchUpInside)
            return c
        }
        let v = UIStackView(arrangedSubviews: [PanelKit.header(title, coat: coat), PanelKit.grid(cards, columns: columns, spacing: 8)])
        v.axis = .vertical
        v.spacing = 4
        PanelKit.pin(v, in: self, insets: UIEdgeInsets(top: 0, left: 8, bottom: 8, right: 8))
    }

    required init?(coder: NSCoder) { fatalError() }
}

/// Stretch: keyboard height, live, in place of the Whisker bar.
final class StretchBar: UIView {
    private let label = UILabel()
    private var value: Int
    private let onChange: (Int) -> Void

    init(coat: Coat, start: Int, onChange: @escaping (Int) -> Void, onDone: @escaping () -> Void) {
        value = start
        self.onChange = onChange
        super.init(frame: .zero)
        backgroundColor = coat.background
        label.font = .systemFont(ofSize: 14, weight: .semibold)
        label.textColor = coat.keyText
        label.textAlignment = .center
        let minus = PanelKit.button(nil, symbol: "minus", coat: coat) { [weak self] in self?.set((self?.value ?? 100) - 5) }
        let plus = PanelKit.button(nil, symbol: "plus", coat: coat) { [weak self] in self?.set((self?.value ?? 100) + 5) }
        let reset = PanelKit.button("Reset", coat: coat, size: 14) { [weak self] in self?.set(100) }
        let done = PanelKit.button(nil, symbol: "checkmark", coat: coat, background: coat.accent, tint: coat.onAccent) { onDone() }
        let row = UIStackView(arrangedSubviews: [minus, label, plus, reset, done])
        row.spacing = 6
        for (v, w) in [(minus, 44.0), (plus, 44.0), (reset, 64.0), (done, 44.0)] { v.widthAnchor.constraint(equalToConstant: w).isActive = true }
        PanelKit.pin(row, in: self, insets: UIEdgeInsets(top: 6, left: 6, bottom: 6, right: 6))
        update()
    }

    required init?(coder: NSCoder) { fatalError() }

    private func set(_ v: Int) { value = min(140, max(70, v)); update(); onChange(value) }
    private func update() { label.text = "Stretch  \(value)%" }
}

/// Settings, inside the keyboard (iPhone keyboards and their apps can't share settings
/// without a paid developer account, so they live here).
final class SettingsPanel: UIView {
    enum Row {
        case toggle(String, Bool, (Bool) -> Void)
        case choice(String, String, () -> Void)
        case section(String)
        case note(String)
    }

    init(coat: Coat, rows: [Row], onBack: @escaping () -> Void) {
        super.init(frame: .zero)
        backgroundColor = coat.background
        let list = UIStackView()
        list.axis = .vertical
        list.spacing = 2
        for r in rows {
            switch r {
            case .section(let t):
                let l = PanelKit.label(t, coat: coat, size: 12.5, weight: .semibold, color: coat.accent)
                let wrap = UIView()
                PanelKit.pin(l, in: wrap, insets: UIEdgeInsets(top: 12, left: 6, bottom: 2, right: 6))
                list.addArrangedSubview(wrap)
            case .note(let t):
                let l = PanelKit.label(t, coat: coat, size: 12, weight: .regular, color: coat.hintText)
                l.numberOfLines = 0
                let wrap = UIView()
                PanelKit.pin(l, in: wrap, insets: UIEdgeInsets(top: 2, left: 6, bottom: 4, right: 6))
                list.addArrangedSubview(wrap)
            case .toggle(let t, let on, let change):
                let s = UISwitch()
                s.isOn = on
                s.onTintColor = coat.accent
                s.addAction(UIAction { [weak s] _ in change(s?.isOn ?? false) }, for: .valueChanged)
                list.addArrangedSubview(row(t, trailing: s, coat: coat))
            case .choice(let t, let value, let tap):
                let b = UIButton(type: .system)
                b.setTitle(value + "  ›", for: .normal)
                b.titleLabel?.font = .systemFont(ofSize: 14)
                b.tintColor = coat.accent
                b.addAction(UIAction { _ in tap() }, for: .touchUpInside)
                list.addArrangedSubview(row(t, trailing: b, coat: coat))
            }
        }
        let scroll = UIScrollView()
        scroll.showsVerticalScrollIndicator = false
        PanelKit.pin(list, in: scroll)
        list.widthAnchor.constraint(equalTo: scroll.frameLayoutGuide.widthAnchor).isActive = true
        let done = PanelKit.button("Done", coat: coat) { onBack() }
        done.heightAnchor.constraint(equalToConstant: 40).isActive = true
        let v = UIStackView(arrangedSubviews: [PanelKit.header("Settings", coat: coat), scroll, done])
        v.axis = .vertical
        v.spacing = 4
        PanelKit.pin(v, in: self, insets: UIEdgeInsets(top: 0, left: 10, bottom: 4, right: 10))
    }

    required init?(coder: NSCoder) { fatalError() }

    private func row(_ title: String, trailing: UIView, coat: Coat) -> UIView {
        let l = PanelKit.label(title, coat: coat, size: 15, weight: .regular)
        let r = UIStackView(arrangedSubviews: [l, UIView(), trailing])
        r.alignment = .center
        r.isLayoutMarginsRelativeArrangement = true
        r.directionalLayoutMargins = NSDirectionalEdgeInsets(top: 0, leading: 6, bottom: 0, trailing: 6)
        r.heightAnchor.constraint(equalToConstant: 44).isActive = true
        return r
    }
}

/// Tricks: short codes that expand into phrases.
final class TricksPanel: UIView {
    init(coat: Coat, onAdd: @escaping () -> Void, onChanged: @escaping () -> Void, onBack: @escaping () -> Void) {
        super.init(frame: .zero)
        backgroundColor = coat.background
        let list = UIStackView()
        list.axis = .vertical
        list.spacing = 6
        for (code, phrase) in Prefs.shared.tricks.sorted(by: { $0.key < $1.key }) {
            let card = UIView()
            card.backgroundColor = coat.key
            card.layer.cornerRadius = 12
            let t = PanelKit.label("\(code)  →  \(phrase)", coat: coat, size: 14, weight: .regular)
            let del = PanelKit.button(nil, symbol: "trash", coat: coat, background: coat.key, tint: coat.hintText) {
                var m = Prefs.shared.tricks
                m.removeValue(forKey: code)
                Prefs.shared.tricks = m
                onChanged()
            }
            del.widthAnchor.constraint(equalToConstant: 36).isActive = true
            let row = UIStackView(arrangedSubviews: [t, del])
            row.alignment = .center
            PanelKit.pin(row, in: card, insets: UIEdgeInsets(top: 6, left: 14, bottom: 6, right: 4))
            list.addArrangedSubview(card)
        }
        let scroll = UIScrollView()
        PanelKit.pin(list, in: scroll)
        list.widthAnchor.constraint(equalTo: scroll.frameLayoutGuide.widthAnchor).isActive = true
        let done = PanelKit.button("Done", coat: coat) { onBack() }
        done.heightAnchor.constraint(equalToConstant: 40).isActive = true
        let v = UIStackView(arrangedSubviews: [PanelKit.header("Tricks", coat: coat, action: "Add") { onAdd() }, scroll, done])
        v.axis = .vertical
        v.spacing = 4
        PanelKit.pin(v, in: self, insets: UIEdgeInsets(top: 0, left: 10, bottom: 4, right: 10))
    }

    required init?(coder: NSCoder) { fatalError() }
}
