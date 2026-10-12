import UIKit

/// Every tool: all appear in the Den, any can be pinned to the Whisker bar.
enum Tool: String, CaseIterable {
    case den, emoji, stickers, gifs, translate, stash, groom, singlePaw, stretch, coats, breeds, numberRow, gestures, sound, haptics, tricks, settings, hide

    var label: String {
        switch self {
        case .den: return "Den"; case .emoji: return "Emoji"; case .stickers: return "Stickers"; case .gifs: return "GIFs"
        case .translate: return "Translate"; case .stash: return "Stash"; case .groom: return "Groom"; case .singlePaw: return "Single paw"
        case .stretch: return "Stretch"; case .coats: return "Coats"; case .breeds: return "Breeds"; case .numberRow: return "Number row"
        case .gestures: return "Gestures"; case .sound: return "Sound"; case .haptics: return "Haptics"; case .tricks: return "Tricks"
        case .settings: return "Settings"; case .hide: return "Hide"
        }
    }

    var symbol: String {
        switch self {
        case .den: return "pawprint.fill"; case .emoji: return "face.smiling"; case .stickers: return "rectangle.on.rectangle.angled"
        case .gifs: return "photo.on.rectangle"; case .translate: return "character.bubble"; case .stash: return "doc.on.clipboard"
        case .groom: return "text.cursor"; case .singlePaw: return "hand.raised"; case .stretch: return "arrow.up.and.down"
        case .coats: return "paintpalette"; case .breeds: return "keyboard"; case .numberRow: return "number"
        case .gestures: return "scribble.variable"; case .sound: return "speaker.wave.2"; case .haptics: return "iphone.radiowaves.left.and.right"
        case .tricks: return "text.badge.plus"; case .settings: return "gearshape"; case .hide: return "keyboard.chevron.compact.down"
        }
    }

    var pinnable: Bool { self != .den && self != .hide }
    static let defaultBar: [Tool] = [.emoji, .stash, .groom, .translate, .stickers]
    static let maxPinned = 5
}

protocol WhiskerBarDelegate: AnyObject {
    func toolTapped(_ tool: Tool)
    func hunchPicked(_ index: Int, _ s: Suggestion)
    func hunchLongPressed(_ s: Suggestion)
}

/// The bar above the keys: Den, pinned tools and Hide when idle; three Hunches
/// (suggestions, the strongest in the middle) while typing.
final class WhiskerBar: UIView {
    weak var delegate: WhiskerBarDelegate?
    var coat = Coat.calico { didSet { setNeedsDisplay() } }
    var tools: [Tool] = Tool.defaultBar { didSet { setNeedsDisplay() } }
    var activeTool: Tool? { didSet { setNeedsDisplay() } }
    /// A one-line message shown instead of Hunches (e.g. "Erase 2 words").
    var message: String? { didSet { setNeedsDisplay() } }

    private var hunches: [Suggestion] = []
    private var autoIndex = -1
    private var slots: [CGRect] = []
    private var slotTools: [Tool] = []
    private var pressed = -1
    private var longPressed = false
    private var longPressWork: DispatchWorkItem?

    override init(frame: CGRect) {
        super.init(frame: frame)
        isOpaque = true
        contentMode = .redraw
    }

    required init?(coder: NSCoder) { fatalError() }

    var showingHunches: Bool { !hunches.isEmpty }

    func showTools() { hunches = []; setNeedsDisplay() }

    func show(hunches list: [Suggestion], autoIndex: Int) {
        hunches = list
        self.autoIndex = autoIndex
        setNeedsDisplay()
    }

    private func slotOrder(_ n: Int) -> [Int] { n == 1 ? [0] : (n == 2 ? [1, 0] : [1, 0, 2]) }

    override func draw(_ rect: CGRect) {
        coat.background.setFill()
        UIRectFill(bounds)
        slots = []
        slotTools = []
        if let m = message {
            text(m, in: bounds, size: 15, weight: .medium, color: coat.keyText)
        } else if hunches.isEmpty {
            drawTools()
        } else {
            drawHunches()
        }
    }

    private func drawTools() {
        let side: CGFloat = 8, cell: CGFloat = 48
        var items: [(Tool, CGFloat)] = [(.den, side + cell / 2), (.hide, bounds.width - side - cell / 2)]
        let middle = tools.filter { $0.pinnable }
        if !middle.isEmpty {
            let left = side + cell, span = bounds.width - 2 * side - 2 * cell
            let step = span / CGFloat(middle.count)
            for (i, t) in middle.enumerated() { items.append((t, left + step * (CGFloat(i) + 0.5))) }
        }
        let current: Tool? = activeTool.map { act in items.contains { $0.0 == act } ? act : .den }
        for (t, cx) in items {
            let r = CGRect(x: cx - cell / 2, y: 0, width: cell, height: bounds.height)
            let idx = slots.count
            slots.append(r)
            slotTools.append(t)
            let active = t == current
            if active || idx == pressed {
                (active ? coat.accent.withAlphaComponent(0.14) : coat.keyPressed).setFill()
                UIBezierPath(roundedRect: CGRect(x: cx - 20, y: bounds.midY - 15, width: 40, height: 30), cornerRadius: 15).fill()
            }
            let color = (t == .den || active) ? coat.accent : coat.stripText
            let cfg = UIImage.SymbolConfiguration(pointSize: 17, weight: .regular)
            if let img = UIImage(systemName: t.symbol, withConfiguration: cfg)?.withTintColor(color, renderingMode: .alwaysOriginal) {
                img.draw(at: CGPoint(x: cx - img.size.width / 2, y: bounds.midY - img.size.height / 2))
            }
        }
    }

    private func drawHunches() {
        let n = min(3, hunches.count)
        let order = slotOrder(n)
        let w = bounds.width / 3
        let offset = CGFloat(3 - n) * w / 2
        for s in 0..<n {
            let r = CGRect(x: offset + CGFloat(s) * w, y: 0, width: w, height: bounds.height)
            slots.append(r)
            if s == pressed {
                coat.keyPressed.setFill()
                UIBezierPath(roundedRect: r.insetBy(dx: 6, dy: 6), cornerRadius: 12).fill()
            }
            if s > 0 {
                coat.hintText.withAlphaComponent(0.18).setFill()
                UIRectFill(CGRect(x: r.minX, y: bounds.height * 0.36, width: 1, height: bounds.height * 0.28))
            }
            let idx = order[s]
            let h = hunches[idx]
            let auto = idx == autoIndex
            let color = auto ? coat.accent : (idx == 0 ? coat.keyText : coat.stripText)
            text(h.isShortcut ? "↪ \(h.word)" : h.word, in: r.insetBy(dx: 8, dy: 0), size: 15.5, weight: idx == 0 ? .medium : .regular, color: color)
        }
    }

    private func text(_ s: String, in r: CGRect, size: CGFloat, weight: UIFont.Weight, color: UIColor) {
        let style = NSMutableParagraphStyle()
        style.alignment = .center
        style.lineBreakMode = .byTruncatingTail
        let attrs: [NSAttributedString.Key: Any] = [.font: UIFont.systemFont(ofSize: size, weight: weight), .foregroundColor: color, .paragraphStyle: style]
        let h = (s as NSString).size(withAttributes: attrs).height
        (s as NSString).draw(in: CGRect(x: r.minX, y: r.midY - h / 2, width: r.width, height: h), withAttributes: attrs)
    }

    // MARK: Touches

    override func touchesBegan(_ touches: Set<UITouch>, with event: UIEvent?) {
        guard let p = touches.first?.location(in: self) else { return }
        pressed = slots.firstIndex { $0.contains(p) } ?? -1
        longPressed = false
        let work = DispatchWorkItem { [weak self] in
            guard let self = self, !self.hunches.isEmpty, let s = self.hunch(at: self.pressed) else { return }
            self.longPressed = true
            self.delegate?.hunchLongPressed(s)
        }
        longPressWork = work
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.5, execute: work)
        setNeedsDisplay()
    }

    override func touchesMoved(_ touches: Set<UITouch>, with event: UIEvent?) {
        guard let p = touches.first?.location(in: self) else { return }
        if (slots.firstIndex { $0.contains(p) } ?? -1) != pressed { longPressWork?.cancel(); pressed = -1; setNeedsDisplay() }
    }

    override func touchesEnded(_ touches: Set<UITouch>, with event: UIEvent?) {
        longPressWork?.cancel()
        let slot = pressed
        pressed = -1
        setNeedsDisplay()
        guard slot >= 0, !longPressed, message == nil else { return }
        if hunches.isEmpty {
            if slot < slotTools.count { delegate?.toolTapped(slotTools[slot]) }
        } else {
            let order = slotOrder(min(3, hunches.count))
            if slot < order.count { delegate?.hunchPicked(order[slot], hunches[order[slot]]) }
        }
    }

    override func touchesCancelled(_ touches: Set<UITouch>, with event: UIEvent?) {
        longPressWork?.cancel(); pressed = -1; setNeedsDisplay()
    }

    private func hunch(at slot: Int) -> Suggestion? {
        let order = slotOrder(min(3, hunches.count))
        return slot >= 0 && slot < order.count ? hunches[order[slot]] : nil
    }
}
