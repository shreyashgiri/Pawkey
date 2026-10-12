import UIKit

/// A Den tool: an icon in a soft circle with a small label (and a pin badge while customizing).
final class ToolTile: UIControl {
    private let circle = UIView()
    private let icon = UIImageView()
    private let title = UILabel()
    private let badge = UILabel()
    private let coat: Coat

    init(tool: Tool, label: String, coat: Coat, on: Bool, badgeText: String?, badgeOn: Bool) {
        self.coat = coat
        super.init(frame: .zero)
        accessibilityLabel = label
        circle.backgroundColor = on ? coat.accent.withAlphaComponent(0.18) : coat.key
        circle.layer.cornerRadius = 20
        circle.isUserInteractionEnabled = false
        if coat.id == Coat.contrast.id { circle.layer.borderWidth = 1; circle.layer.borderColor = coat.border.cgColor }
        icon.image = UIImage(systemName: tool.symbol, withConfiguration: UIImage.SymbolConfiguration(pointSize: 17, weight: .regular))
        icon.tintColor = on ? coat.accent : coat.keyText
        icon.contentMode = .center
        title.text = label
        title.font = .systemFont(ofSize: 11.5)
        title.textColor = coat.stripText
        title.textAlignment = .center
        title.adjustsFontSizeToFitWidth = true
        title.minimumScaleFactor = 0.8
        for v in [circle, icon, title] { v.translatesAutoresizingMaskIntoConstraints = false; addSubview(v) }
        NSLayoutConstraint.activate([
            circle.centerXAnchor.constraint(equalTo: centerXAnchor), circle.topAnchor.constraint(equalTo: topAnchor, constant: 6),
            circle.widthAnchor.constraint(equalToConstant: 40), circle.heightAnchor.constraint(equalToConstant: 40),
            icon.centerXAnchor.constraint(equalTo: circle.centerXAnchor), icon.centerYAnchor.constraint(equalTo: circle.centerYAnchor),
            title.topAnchor.constraint(equalTo: circle.bottomAnchor, constant: 4),
            title.leadingAnchor.constraint(equalTo: leadingAnchor, constant: 2), title.trailingAnchor.constraint(equalTo: trailingAnchor, constant: -2),
        ])
        if let b = badgeText {
            badge.text = b
            badge.font = .systemFont(ofSize: 11, weight: .semibold)
            badge.textAlignment = .center
            badge.textColor = badgeOn ? coat.onAccent : coat.hintText
            badge.backgroundColor = badgeOn ? coat.accent : coat.functionKey
            badge.layer.cornerRadius = 9
            badge.clipsToBounds = true
            badge.translatesAutoresizingMaskIntoConstraints = false
            addSubview(badge)
            NSLayoutConstraint.activate([
                badge.widthAnchor.constraint(equalToConstant: 18), badge.heightAnchor.constraint(equalToConstant: 18),
                badge.topAnchor.constraint(equalTo: circle.topAnchor, constant: -4),
                badge.leadingAnchor.constraint(equalTo: circle.trailingAnchor, constant: -10),
            ])
        }
    }

    required init?(coder: NSCoder) { fatalError() }

    override var isHighlighted: Bool { didSet { alpha = isHighlighted ? 0.6 : 1 } }
}

/// The Den: every tool one tap away. Customize pins tools to the Whisker bar.
final class DenPanel: UIView {
    private let coat: Coat
    private let prefs = Prefs.shared
    private let onTool: (Tool) -> Void
    private let onPinsChanged: () -> Void
    private var customizing = false
    private let scroll = UIScrollView()
    private let stack = UIStackView()

    init(coat: Coat, onTool: @escaping (Tool) -> Void, onPinsChanged: @escaping () -> Void) {
        self.coat = coat
        self.onTool = onTool
        self.onPinsChanged = onPinsChanged
        super.init(frame: .zero)
        backgroundColor = coat.background
        stack.axis = .vertical
        scroll.showsVerticalScrollIndicator = false
        PanelKit.pin(scroll, in: self, insets: UIEdgeInsets(top: 0, left: 6, bottom: 4, right: 6))
        stack.translatesAutoresizingMaskIntoConstraints = false
        scroll.addSubview(stack)
        NSLayoutConstraint.activate([
            stack.topAnchor.constraint(equalTo: scroll.contentLayoutGuide.topAnchor),
            stack.bottomAnchor.constraint(equalTo: scroll.contentLayoutGuide.bottomAnchor),
            stack.leadingAnchor.constraint(equalTo: scroll.frameLayoutGuide.leadingAnchor),
            stack.trailingAnchor.constraint(equalTo: scroll.frameLayoutGuide.trailingAnchor),
        ])
        render()
    }

    required init?(coder: NSCoder) { fatalError() }

    private func render() {
        stack.arrangedSubviews.forEach { $0.removeFromSuperview() }
        stack.addArrangedSubview(customizing
            ? PanelKit.header("Tap to pin to your bar", coat: coat, action: "Done") { [weak self] in self?.customizing = false; self?.render() }
            : PanelKit.header("Den", coat: coat, action: "Customize") { [weak self] in self?.customizing = true; self?.render() })
        let pinned = prefs.toolbar
        let tiles: [UIView] = Tool.allCases.filter { $0.pinnable }.map { t in
            let on: Bool = {
                switch t {
                case .numberRow: return prefs.numberRow
                case .sound: return prefs.sound != "off"
                case .haptics: return prefs.haptics
                case .singlePaw: return prefs.singlePaw != "off"
                case .gestures: return prefs.gestures != "off"
                default: return false
                }
            }()
            let label: String = {
                switch t {
                case .gestures: return ["both": "Flicks+Prowl", "prowl": "Prowl", "off": "No gestures"][prefs.gestures] ?? "Flicks"
                case .sound: return Feedback.label(prefs.sound)
                default: return t.label
                }
            }()
            let index = pinned.firstIndex(of: t.rawValue)
            let tile = ToolTile(tool: t, label: label, coat: coat, on: customizing ? index != nil : on,
                                badgeText: customizing ? (index.map { "\($0 + 1)" } ?? "+") : nil, badgeOn: index != nil)
            tile.addAction(UIAction { [weak self] _ in
                guard let self = self else { return }
                if self.customizing { self.togglePin(t) } else { self.onTool(t) }
            }, for: .touchUpInside)
            tile.heightAnchor.constraint(equalToConstant: 74).isActive = true
            return tile
        }
        stack.addArrangedSubview(PanelKit.grid(tiles, columns: 5, spacing: 2))
        if customizing {
            let reset = PanelKit.button("Reset bar", coat: coat, background: coat.background, tint: coat.hintText, size: 13, weight: .regular) { [weak self] in
                self?.prefs.toolbar = Tool.defaultBar.map { $0.rawValue }
                self?.onPinsChanged()
                self?.render()
            }
            reset.heightAnchor.constraint(equalToConstant: 40).isActive = true
            stack.addArrangedSubview(reset)
        }
    }

    private func togglePin(_ t: Tool) {
        var list = prefs.toolbar
        if let i = list.firstIndex(of: t.rawValue) { list.remove(at: i) }
        else if list.count < Tool.maxPinned { list.append(t.rawValue) }
        else { return }
        prefs.toolbar = list
        onPinsChanged()
        render()
    }
}

/// A one-line entry bar in place of the Whisker bar. Typing goes here instead of the
/// app: used for emoji and GIF search, the GIPHY key and adding Tricks.
final class EntryBar: UIView {
    private let field = UILabel()
    private let results = UIStackView()
    private let resultsScroll = UIScrollView()
    private let coat: Coat
    private let placeholder: String
    var text = "" { didSet { refresh() } }

    init(coat: Coat, placeholder: String, symbol: String = "magnifyingglass", showsResults: Bool, onClose: @escaping () -> Void) {
        self.coat = coat
        self.placeholder = placeholder
        super.init(frame: .zero)
        backgroundColor = coat.background
        let box = UIView()
        box.backgroundColor = coat.key
        box.layer.cornerRadius = 18
        let icon = UIImageView(image: UIImage(systemName: symbol))
        icon.tintColor = coat.hintText
        field.font = .systemFont(ofSize: 15)
        field.lineBreakMode = .byTruncatingHead
        let close = PanelKit.button(nil, symbol: "xmark", coat: coat, background: coat.key, tint: coat.hintText) { onClose() }
        let row = UIStackView(arrangedSubviews: [icon, field, close])
        row.spacing = 8
        row.alignment = .center
        PanelKit.pin(row, in: box, insets: UIEdgeInsets(top: 0, left: 12, bottom: 0, right: 2))
        close.widthAnchor.constraint(equalToConstant: 34).isActive = true
        let outer = UIStackView(arrangedSubviews: [box])
        outer.axis = .vertical
        outer.spacing = 2
        box.heightAnchor.constraint(equalToConstant: 38).isActive = true
        if showsResults {
            results.axis = .horizontal
            results.spacing = 2
            resultsScroll.showsHorizontalScrollIndicator = false
            PanelKit.pin(results, in: resultsScroll)
            results.heightAnchor.constraint(equalTo: resultsScroll.frameLayoutGuide.heightAnchor).isActive = true
            resultsScroll.heightAnchor.constraint(equalToConstant: 44).isActive = true
            outer.addArrangedSubview(resultsScroll)
        }
        PanelKit.pin(outer, in: self, insets: UIEdgeInsets(top: 5, left: 8, bottom: 3, right: 8))
        refresh()
    }

    required init?(coder: NSCoder) { fatalError() }

    private func refresh() {
        field.text = text.isEmpty ? placeholder : text
        field.textColor = text.isEmpty ? coat.hintText : coat.keyText
    }

    func showResults(_ items: [String], onPick: @escaping (String) -> Void) {
        results.arrangedSubviews.forEach { $0.removeFromSuperview() }
        if !text.isEmpty && items.isEmpty {
            results.addArrangedSubview(PanelKit.label("  No emoji found", coat: coat, size: 14, weight: .regular, color: coat.hintText))
        }
        for e in items {
            let b = UIButton(type: .system)
            b.setTitle(e, for: .normal)
            b.titleLabel?.font = .systemFont(ofSize: 26)
            b.addAction(UIAction { _ in onPick(e) }, for: .touchUpInside)
            b.widthAnchor.constraint(equalToConstant: 46).isActive = true
            results.addArrangedSubview(b)
        }
    }
}
