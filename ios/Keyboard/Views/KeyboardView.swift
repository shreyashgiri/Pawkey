import UIKit

enum ShiftState { case off, on, locked }
enum Flick { case left, right, up, down }

protocol KeyboardViewDelegate: AnyObject {
    func keyDown(_ key: Key)
    func keyTapped(_ key: Key)
    func popupChosen(_ key: Key, text: String)
    func flicked(_ flick: Flick)
    func spaceCursor(_ steps: Int)
    func spaceLongPressed()
    func deleteSwipe(words: Int)
    func deleteSwipeEnded(words: Int)
    func glided(_ points: [CGPoint])
}

/// Draws the keys and turns touches into taps, long-press popups, Flicks, Prowl
/// (swipe typing), Scoot (spacebar cursor) and Scratch (swipe-left delete).
/// It also covers the Whisker bar area (transparent, touch-through) so key pop-ups
/// and the Prowl trail can float above the top row.
final class KeyboardView: UIView {
    weak var delegate: KeyboardViewDelegate?

    var coat = Coat.calico { didSet { setNeedsDisplay() } }
    var breed = Breed.pawkeys { didSet { setNeedsLayout(); setNeedsDisplay() } }
    var layout: KeyboardLayout? { didSet { cancelGesture(); layoutKeys(); setNeedsDisplay() } }
    var shift = ShiftState.off { didSet { if shift != oldValue { setNeedsDisplay() } } }
    var enterSymbol = "return" { didSet { setNeedsDisplay() } }
    var enterLabel = "return" { didSet { setNeedsDisplay() } }
    var enterIsAction = false { didSet { setNeedsDisplay() } }
    var showHints = false
    var keyOutlines = false
    var heightScale: CGFloat = 1
    var gestures = "flicks"
    var scoot = true
    var scratch = true
    var keyPopup = true
    var runningCat = true
    /// Height of the transparent area above the keys (the Whisker bar).
    var topInset: CGFloat = 48 { didSet { layoutKeys() } }

    private(set) var unitWidth: CGFloat = 0
    private var rowHeight: CGFloat { breed.rowHeight * heightScale }

    var keysHeight: CGFloat { breed.padTop + breed.padBottom + CGFloat(layout?.rows.count ?? 4) * rowHeight }

    // Gesture state.
    private enum Mode { case none, tap, popup, spaceCursor, deleteSwipe, deleteRepeat, glide, flick, consumed }
    private var mode = Mode.none
    private var activeTouch: UITouch?
    private var downKey: Key?
    private var downPoint = CGPoint.zero
    private var lastCursorX: CGFloat = 0
    private var deleteWords = 0
    private var pressedKey: Key?
    private var previewKey: Key?
    private var trace: [CGPoint] = []
    private var popupKey: Key?
    private var popupItems: [String] = []
    private var popupCells: [CGRect] = []
    private var popupSelected = 0
    private var longPressWork: DispatchWorkItem?
    private var repeatTimer: Timer?
    private var repeatCount = 0
    private let prowl = ProwlTrail()
    private var displayLink: CADisplayLink?

    override init(frame: CGRect) {
        super.init(frame: frame)
        isOpaque = false
        backgroundColor = .clear
        isMultipleTouchEnabled = true
        contentMode = .redraw
    }

    required init?(coder: NSCoder) { fatalError() }

    /// Only the key area takes touches; the Whisker bar underneath gets the rest.
    override func point(inside point: CGPoint, with event: UIEvent?) -> Bool {
        point.y >= topInset && super.point(inside: point, with: event)
    }

    override func layoutSubviews() {
        super.layoutSubviews()
        layoutKeys()
    }

    private func layoutKeys() {
        guard let rows = layout?.rows, bounds.width > 0 else { return }
        let avail = bounds.width - 2 * breed.padSide
        let maxUnits = rows.map { $0.reduce(0) { $0 + $1.width } }.max() ?? 10
        unitWidth = avail / maxUnits
        for (r, row) in rows.enumerated() {
            let units = row.reduce(0) { $0 + $1.width }
            var x = breed.padSide + (maxUnits - units) * unitWidth / 2
            let top = topInset + breed.padTop + CGFloat(r) * rowHeight
            for key in row {
                key.frame = CGRect(x: x, y: top, width: key.width * unitWidth, height: rowHeight)
                x += key.width * unitWidth
            }
        }
        setNeedsDisplay()
    }

    /// Letter key centres for the Prowl decoder.
    func letterCenters() -> [UInt8: CGPoint] {
        var m: [UInt8: CGPoint] = [:]
        layout?.rows.forEach { $0.forEach { k in
            if k.isLetter, let a = k.label.lowercased().utf8.first { m[a] = CGPoint(x: k.frame.midX, y: k.frame.midY) }
        } }
        return m
    }

    private func key(at p: CGPoint) -> Key? {
        guard let rows = layout?.rows, !rows.isEmpty else { return nil }
        let r = max(0, min(rows.count - 1, Int((p.y - topInset - breed.padTop) / rowHeight)))
        let row = rows[r]
        return row.first { p.x >= $0.frame.minX && p.x < $0.frame.maxX }
            ?? row.min { min(abs(p.x - $0.frame.minX), abs(p.x - $0.frame.maxX)) < min(abs(p.x - $1.frame.minX), abs(p.x - $1.frame.maxX)) }
    }

    // MARK: Drawing

    override func draw(_ rect: CGRect) {
        guard let ctx = UIGraphicsGetCurrentContext() else { return }
        coat.background.setFill()
        ctx.fill(CGRect(x: 0, y: topInset, width: bounds.width, height: bounds.height - topInset))
        layout?.rows.forEach { $0.forEach { drawKey(ctx, $0) } }
        prowl.showCat = runningCat
        if prowl.draw(in: ctx, color: coat.accent, eye: coat.background) { startDisplayLink() } else { stopDisplayLink() }
        if let k = previewKey { drawPreview(ctx, k) }
        if popupKey != nil { drawPopup(ctx) }
    }

    private func keyRect(_ k: Key) -> CGRect { k.frame.insetBy(dx: breed.gapH, dy: breed.gapV) }

    private func drawKey(_ ctx: CGContext, _ key: Key) {
        let r = keyRect(key)
        let pressed = key === pressedKey
        let isEnter = key.code == Code.enter
        let enterAccent: Bool
        switch breed.enter {
        case .accent, .pill: enterAccent = true
        case .function: enterAccent = false
        case .iosText: enterAccent = enterIsAction
        }
        let bg: UIColor
        if isEnter && enterAccent { bg = pressed ? coat.accent.withAlphaComponent(0.75) : coat.accent }
        else if pressed { bg = coat.keyPressed }
        else if key.type == .function || key.type == .action { bg = coat.functionKey }
        else { bg = coat.key }
        let radius = isEnter && breed.enter == .pill ? r.height / 2 : breed.radius
        if breed.keyShadow {
            coat.border.setFill()
            UIBezierPath(roundedRect: r.offsetBy(dx: 0, dy: 1), cornerRadius: radius).fill()
        }
        bg.setFill()
        let path = UIBezierPath(roundedRect: r, cornerRadius: radius)
        path.fill()
        if (keyOutlines && !breed.keyShadow) || coat.id == Coat.contrast.id {
            coat.border.setStroke()
            path.lineWidth = 1
            path.stroke()
        }
        let fg = isEnter && enterAccent ? coat.onAccent : coat.keyText

        if isEnter && breed.enter == .iosText {
            drawText(enterLabel, in: r, size: 15.5, weight: .regular, color: fg)
            return
        }
        switch key.code {
        case Code.shift:
            let (name, tint): (String, UIColor) = {
                switch shift {
                case .off: return ("shift", fg)
                case .on: return ("shift.fill", coat.accent)
                case .locked: return ("capslock.fill", coat.accent)
                }
            }()
            drawSymbol(name, in: r, color: tint)
        case Code.enter:
            drawSymbol(enterSymbol, in: r, color: fg)
        default:
            if key.type == .space {
                let label = breed.spaceLabel ?? "pawkeys"
                drawText(label, in: r, size: breed.spaceLabel == nil ? 11.5 : 14, weight: breed.spaceLabel == nil ? .medium : .regular,
                         color: breed == .iphone ? coat.keyText : coat.hintText, kern: breed.spaceLabel == nil ? 1.4 : 0)
            } else if let s = key.symbol {
                drawSymbol(s, in: r, color: fg)
            } else {
                let label = key.isLetter && shift != .off ? key.label.uppercased() : key.label
                let size: CGFloat
                if key.type == .function { size = 14 }
                else if label.count > 1 { size = 16 }
                else if layout?.id == .numpad || layout?.id == .phone { size = 23 }
                else { size = 22 }
                drawText(label, in: r, size: size, weight: key.type == .function ? .medium : .regular, color: fg)
                if let h = key.hint, showHints || h.count > 1 {
                    let attrs: [NSAttributedString.Key: Any] = [.font: UIFont.systemFont(ofSize: 9.5), .foregroundColor: coat.hintText]
                    let sz = (h as NSString).size(withAttributes: attrs)
                    (h as NSString).draw(at: CGPoint(x: r.maxX - sz.width - 5, y: r.minY + 3), withAttributes: attrs)
                }
            }
        }
    }

    private func drawText(_ s: String, in r: CGRect, size: CGFloat, weight: UIFont.Weight, color: UIColor, kern: CGFloat = 0) {
        let attrs: [NSAttributedString.Key: Any] = [.font: UIFont.systemFont(ofSize: size, weight: weight), .foregroundColor: color, .kern: kern]
        let sz = (s as NSString).size(withAttributes: attrs)
        (s as NSString).draw(at: CGPoint(x: r.midX - sz.width / 2, y: r.midY - sz.height / 2), withAttributes: attrs)
    }

    private func drawSymbol(_ name: String, in r: CGRect, color: UIColor) {
        let cfg = UIImage.SymbolConfiguration(pointSize: 18, weight: .regular)
        guard let img = UIImage(systemName: name, withConfiguration: cfg)?.withTintColor(color, renderingMode: .alwaysOriginal) else { return }
        img.draw(at: CGPoint(x: r.midX - img.size.width / 2, y: r.midY - img.size.height / 2))
    }

    private func drawPreview(_ ctx: CGContext, _ key: Key) {
        let r = keyRect(key)
        let w = r.width * 1.15, h = rowHeight * 1.05
        let left = max(0, min(bounds.width - w, r.midX - w / 2))
        let box = CGRect(x: left, y: r.minY - h - 4, width: w, height: h)
        ctx.saveGState()
        ctx.setShadow(offset: CGSize(width: 0, height: 3), blur: 10, color: UIColor(white: 0, alpha: 0.18).cgColor)
        coat.popup.setFill()
        UIBezierPath(roundedRect: box, cornerRadius: breed.radius * 1.2).fill()
        ctx.restoreGState()
        let label = key.isLetter && shift != .off ? key.label.uppercased() : key.label
        drawText(label, in: box, size: 28, weight: .regular, color: coat.keyText)
    }

    private func drawPopup(_ ctx: CGContext) {
        guard let first = popupCells.first else { return }
        let box = popupCells.reduce(first) { $0.union($1) }.insetBy(dx: -4, dy: -4)
        ctx.saveGState()
        ctx.setShadow(offset: CGSize(width: 0, height: 4), blur: 14, color: UIColor(white: 0, alpha: 0.22).cgColor)
        coat.popup.setFill()
        UIBezierPath(roundedRect: box, cornerRadius: breed.radius * 1.2).fill()
        ctx.restoreGState()
        for (i, text) in popupItems.enumerated() {
            let cell = popupCells[i]
            let selected = i == popupSelected
            if selected {
                coat.accent.setFill()
                UIBezierPath(roundedRect: cell, cornerRadius: breed.radius).fill()
            }
            drawText(text, in: cell, size: text.count > 2 ? 14 : 20, weight: .regular, color: selected ? coat.onAccent : coat.keyText)
        }
    }

    private func startDisplayLink() {
        guard displayLink == nil else { return }
        let link = CADisplayLink(target: self, selector: #selector(tick(_:)))
        link.add(to: .main, forMode: .common)
        displayLink = link
    }

    private func stopDisplayLink() {
        displayLink?.invalidate()
        displayLink = nil
    }

    @objc private func tick(_ link: CADisplayLink) { setNeedsDisplay() }

    // MARK: Touches

    override func touchesBegan(_ touches: Set<UITouch>, with event: UIEvent?) {
        for t in touches {
            if activeTouch != nil {
                // Fast typing: a second finger lands before the first lifted.
                if mode == .tap { finishTap() }
                else if mode != .none && downKey?.code != Code.shift { continue }
            }
            start(t)
        }
    }

    override func touchesMoved(_ touches: Set<UITouch>, with event: UIEvent?) {
        guard let t = activeTouch, touches.contains(t) else { return }
        move(t.location(in: self))
    }

    override func touchesEnded(_ touches: Set<UITouch>, with event: UIEvent?) {
        guard let t = activeTouch, touches.contains(t) else { return }
        up(t.location(in: self))
    }

    override func touchesCancelled(_ touches: Set<UITouch>, with event: UIEvent?) {
        cancelGesture()
    }

    private func start(_ t: UITouch) {
        longPressWork?.cancel()
        repeatTimer?.invalidate()
        activeTouch = t
        downPoint = t.location(in: self)
        lastCursorX = downPoint.x
        deleteWords = 0
        repeatCount = 0
        trace = [downPoint]
        guard let k = key(at: downPoint) else { mode = .none; return }
        downKey = k
        pressedKey = k
        mode = .tap
        delegate?.keyDown(k)
        switch k.code {
        case Code.shift:
            delegate?.keyTapped(k)
            mode = .consumed
        case Code.delete:
            repeatTimer = Timer.scheduledTimer(withTimeInterval: 0.38, repeats: false) { [weak self] _ in self?.startRepeat() }
        default:
            let work = DispatchWorkItem { [weak self] in self?.longPress() }
            longPressWork = work
            DispatchQueue.main.asyncAfter(deadline: .now() + (k.type == .space ? 0.45 : 0.3), execute: work)
        }
        previewKey = keyPopup && k.type == .char && layout?.id != .numpad && layout?.id != .phone ? k : nil
        setNeedsDisplay()
    }

    private func startRepeat() {
        guard let k = downKey, mode == .tap || mode == .deleteRepeat else { return }
        mode = .deleteRepeat
        delegate?.keyTapped(k)
        repeatCount += 1
        repeatTimer = Timer.scheduledTimer(withTimeInterval: repeatCount > 15 ? 0.03 : 0.06, repeats: false) { [weak self] _ in self?.startRepeat() }
    }

    private func move(_ p: CGPoint) {
        guard let k = downKey else { return }
        let dx = p.x - downPoint.x, dy = p.y - downPoint.y
        let dist = hypot(dx, dy)
        switch mode {
        case .tap:
            if k.type == .space && scoot && abs(dx) > unitWidth * 0.45 {
                longPressWork?.cancel()
                mode = .spaceCursor
                pressedKey = nil
                lastCursorX = downPoint.x
                move(p)
            } else if k.code == Code.delete && scratch && dx < -unitWidth * 0.5 && repeatCount == 0 {
                repeatTimer?.invalidate()
                mode = .deleteSwipe
                move(p)
            } else if k.type == .char && gestures == "prowl" && k.isLetter && dist > unitWidth * 0.6 {
                longPressWork?.cancel()
                mode = .glide
                pressedKey = nil
                previewKey = nil
                prowl.start(trace[0])
                trace.dropFirst().forEach { prowl.add($0) }
                addTrace(p)
            } else if k.type == .char && gestures == "flicks" && dist > unitWidth * 0.7 {
                longPressWork?.cancel()
                mode = .flick
                pressedKey = nil
                previewKey = nil
                setNeedsDisplay()
            } else if k.type == .char && dist > unitWidth * 0.6, let now = key(at: p), now !== k, now.type == .char {
                // Slid onto another key: follow it.
                longPressWork?.cancel()
                downKey = now; pressedKey = now
                if previewKey != nil { previewKey = now }
                downPoint = p
                let work = DispatchWorkItem { [weak self] in self?.longPress() }
                longPressWork = work
                DispatchQueue.main.asyncAfter(deadline: .now() + 0.3, execute: work)
                setNeedsDisplay()
            }
        case .spaceCursor:
            let step = unitWidth * 0.4
            let steps = Int((p.x - lastCursorX) / step)
            if steps != 0 {
                lastCursorX += CGFloat(steps) * step
                delegate?.spaceCursor(steps)
            }
        case .deleteSwipe:
            let words = max(0, Int(-dx / (unitWidth * 0.8)))
            if words != deleteWords {
                deleteWords = words
                delegate?.deleteSwipe(words: words)
            }
        case .glide:
            addTrace(p)
        case .popup:
            let idx = popupCells.firstIndex { p.x >= $0.minX && p.x < $0.maxX && p.y >= $0.minY - rowHeight && p.y < $0.maxY + rowHeight }
            let sel = idx ?? popupCells.indices.min { abs(popupCells[$0].midX - p.x) + abs(popupCells[$0].midY - p.y) < abs(popupCells[$1].midX - p.x) + abs(popupCells[$1].midY - p.y) } ?? 0
            if sel != popupSelected { popupSelected = sel; setNeedsDisplay() }
        default:
            break
        }
    }

    private func addTrace(_ p: CGPoint) {
        trace.append(p)
        if mode == .glide { prowl.add(p) }
        setNeedsDisplay()
    }

    private func up(_ p: CGPoint) {
        longPressWork?.cancel()
        repeatTimer?.invalidate()
        let k = downKey
        switch mode {
        case .tap:
            if k != nil { finishTap() }
        case .popup:
            if let pk = popupKey, popupSelected < popupItems.count {
                let t = popupItems[popupSelected]
                delegate?.popupChosen(pk, text: pk.isLetter && shift != .off ? t.uppercased() : t)
            }
        case .deleteSwipe:
            delegate?.deleteSwipeEnded(words: deleteWords)
        case .glide:
            addTrace(p)
            prowl.release()
            delegate?.glided(trace)
        case .flick:
            let dx = p.x - downPoint.x, dy = p.y - downPoint.y
            if hypot(dx, dy) > unitWidth * 0.7 {
                delegate?.flicked(abs(dx) >= abs(dy) ? (dx > 0 ? .right : .left) : (dy > 0 ? .down : .up))
            } else if let k = k {
                delegate?.keyTapped(k)
            }
        default:
            break
        }
        resetGesture()
    }

    private func finishTap() {
        guard let k = downKey else { return }
        longPressWork?.cancel()
        repeatTimer?.invalidate()
        delegate?.keyTapped(k)
        pressedKey = nil
        mode = .none
        hidePreviewSoon()
    }

    private func hidePreviewSoon() {
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.06) { [weak self] in
            guard let self = self, self.mode != .tap else { return }
            self.previewKey = nil
            self.setNeedsDisplay()
        }
        setNeedsDisplay()
    }

    private func longPress() {
        guard let k = downKey, mode == .tap else { return }
        if k.type == .space {
            mode = .consumed
            pressedKey = nil
            delegate?.spaceLongPressed()
            setNeedsDisplay()
            return
        }
        guard !k.popupChars.isEmpty else { return }
        mode = .popup
        previewKey = nil
        popupKey = k
        popupItems = k.popupChars
        popupSelected = 0
        popupCells = []
        let kr = keyRect(k)
        var cellW = max(kr.width, 34)
        if popupItems.contains(where: { $0.count > 2 }) { cellW = max(cellW, 48) }
        let cellH = rowHeight * 0.92
        let fit = max(1, Int((bounds.width - 16) / cellW))
        let rowsN = (popupItems.count + fit - 1) / fit
        let perRow = (popupItems.count + rowsN - 1) / rowsN
        let totalW = CGFloat(perRow) * cellW
        let left = max(4, min(bounds.width - totalW - 4, kr.midX - cellW / 2))
        // Float above the key, but not above the top of the keyboard.
        let bottom = max(kr.minY - 6, CGFloat(rowsN) * cellH + 4)
        for i in popupItems.indices {
            let r = i / perRow, c = i % perRow
            popupCells.append(CGRect(x: left + CGFloat(c) * cellW, y: bottom - CGFloat(rowsN - r) * cellH, width: cellW, height: cellH))
        }
        delegate?.keyDown(k)
        setNeedsDisplay()
    }

    private func resetGesture() {
        mode = .none
        downKey = nil
        pressedKey = nil
        popupKey = nil
        popupCells = []
        activeTouch = nil
        trace = []
        hidePreviewSoon()
    }

    func cancelGesture() {
        longPressWork?.cancel()
        repeatTimer?.invalidate()
        if mode == .deleteSwipe { delegate?.deleteSwipeEnded(words: 0) }
        prowl.clear()
        previewKey = nil
        resetGesture()
    }
}
