import UIKit

/// The PawKeys keyboard. Same behaviour as the Android service: Hunches, Auto-fix
/// with backspace undo, Flicks, Prowl, Scoot, Scratch, Breeds and Coats.
/// iPhone keyboards edit text through `textDocumentProxy`, so the word being typed
/// is read back from the text before the cursor instead of being "composed".
final class KeyboardViewController: UIInputViewController, KeyboardViewDelegate, WhiskerBarDelegate {
    let prefs = Prefs.shared
    let feedback = Feedback()
    let memory = UserDictionary()
    private(set) var dictionary: WordDictionary?
    private var glide: GlideDecoder?

    let keyboardView = KeyboardView()
    let whisker = WhiskerBar()
    /// Panels and replacement bars go here (above the keyboard view's key area).
    let panelHost = UIView()
    let barHost = UIView()
    private var heightConstraint: NSLayoutConstraint?
    private var keyboardLeading: NSLayoutConstraint?
    private var keyboardTrailing: NSLayoutConstraint?
    var rail: UIView?
    static let barHeight: CGFloat = 48

    var coat = Coat.calico
    var breed = Breed.pawkeys

    // Field state.
    var layoutId = LayoutId.qwerty
    private var baseLayout = LayoutId.qwerty
    private var variant = FieldVariant.text
    private var hunchesOn = true
    private var autoFixOn = true
    private var learningOn = true
    private var autoCapOn = true
    var shift = ShiftState.off
    private var manualShift = false
    private var lastShiftTap: CFTimeInterval = 0

    // Typing state.
    private struct Correction { let original: String; let corrected: String; let separator: String }
    private final class Commit { let options: [String]; var index: Int; let separator: String
        init(_ o: [String], _ i: Int, _ s: String) { options = o; index = i; separator = s } }
    private var lastCorrection: Correction?
    private var lastCommit: Commit?
    private var lastAutoSpace = false
    private var lastKeyWasSpace = false
    private var punctIndex = -1
    private var glideWord: String?
    private var glideAlternatives: [String] = []
    private(set) var currentHunches: [Suggestion] = []
    private static let punctuation = [". ", ", ", "? ", "! ", ": ", "; "]

    // MARK: Lifecycle

    override func loadView() {
        let v = PawInputView(frame: .zero, inputViewStyle: .keyboard)
        v.allowsSelfSizing = true
        inputView = v
    }

    override func viewDidLoad() {
        super.viewDidLoad()
        guard let root = inputView else { return }
        for v in [whisker, barHost, panelHost, keyboardView] {
            v.translatesAutoresizingMaskIntoConstraints = false
            root.addSubview(v)
        }
        whisker.delegate = self
        keyboardView.delegate = self
        keyboardView.topInset = Self.barHeight
        panelHost.isHidden = true
        barHost.isHidden = true

        keyboardLeading = keyboardView.leadingAnchor.constraint(equalTo: root.leadingAnchor)
        keyboardTrailing = keyboardView.trailingAnchor.constraint(equalTo: root.trailingAnchor)
        let h = root.heightAnchor.constraint(equalToConstant: 300)
        h.priority = UILayoutPriority(999)
        heightConstraint = h
        NSLayoutConstraint.activate([
            h,
            whisker.topAnchor.constraint(equalTo: root.topAnchor),
            whisker.leadingAnchor.constraint(equalTo: root.leadingAnchor),
            whisker.trailingAnchor.constraint(equalTo: root.trailingAnchor),
            whisker.heightAnchor.constraint(equalToConstant: Self.barHeight),
            barHost.topAnchor.constraint(equalTo: root.topAnchor),
            barHost.leadingAnchor.constraint(equalTo: root.leadingAnchor),
            barHost.trailingAnchor.constraint(equalTo: root.trailingAnchor),
            barHost.heightAnchor.constraint(equalToConstant: Self.barHeight),
            keyboardView.topAnchor.constraint(equalTo: root.topAnchor),
            keyboardView.bottomAnchor.constraint(equalTo: root.bottomAnchor),
            keyboardLeading!, keyboardTrailing!,
            panelHost.topAnchor.constraint(equalTo: whisker.bottomAnchor),
            panelHost.leadingAnchor.constraint(equalTo: root.leadingAnchor),
            panelHost.trailingAnchor.constraint(equalTo: root.trailingAnchor),
            panelHost.bottomAnchor.constraint(equalTo: root.bottomAnchor),
        ])

        DispatchQueue.global(qos: .userInitiated).async { [weak self] in
            guard let url = Bundle.main.url(forResource: "words_en", withExtension: "txt"),
                  let text = try? String(contentsOf: url, encoding: .utf8) else { return }
            let d = WordDictionary(text: text)
            let g = GlideDecoder(dictionary: d)
            DispatchQueue.main.async { self?.dictionary = d; self?.glide = g }
        }
    }

    override func viewWillAppear(_ animated: Bool) {
        super.viewWillAppear(animated)
        feedback.hasFullAccess = hasFullAccess
        configureForField()
        applyLook()
        resetTyping()
        closePanel()
        setLayout(baseLayout)
        updateShift()
        updateHunches()
    }

    override func viewWillDisappear(_ animated: Bool) {
        super.viewWillDisappear(animated)
        keyboardView.cancelGesture()
        memory.save()
    }

    override func traitCollectionDidChange(_ previous: UITraitCollection?) {
        super.traitCollectionDidChange(previous)
        applyLook()
    }

    override func textDidChange(_ textInput: UITextInput?) {
        super.textDidChange(textInput)
        // The app changed the text or moved the cursor: re-read the field.
        configureForField()
        if !manualShift { updateShift() }
        updateHunches()
    }

    // MARK: Look

    var isDark: Bool {
        if textDocumentProxy.keyboardAppearance == .dark { return true }
        return traitCollection.userInterfaceStyle == .dark
    }

    func applyLook() {
        breed = prefs.breed
        coat = Coat.resolve(prefs.coat, breed: breed, dark: isDark)
        inputView?.backgroundColor = coat.background
        whisker.coat = coat
        whisker.tools = prefs.toolbar.compactMap { Tool(rawValue: $0) }.filter { $0.pinnable }
        keyboardView.coat = coat
        keyboardView.breed = breed
        keyboardView.showHints = prefs.hints
        keyboardView.keyOutlines = prefs.keyOutlines
        keyboardView.heightScale = CGFloat(prefs.height) / 100 * (view.bounds.width > view.bounds.height && view.bounds.height > 0 ? 0.8 : 1)
        keyboardView.gestures = prefs.gestures
        keyboardView.scoot = prefs.scoot
        keyboardView.scratch = prefs.scratch
        keyboardView.keyPopup = prefs.keyPopup
        keyboardView.runningCat = prefs.runningCat
        applySinglePaw()
        setLayout(layoutId)
    }

    private func applySinglePaw() {
        rail?.removeFromSuperview()
        rail = nil
        let side = prefs.singlePaw
        let width = view.bounds.width > 0 ? view.bounds.width : UIScreen.main.bounds.width
        let inset = side == "off" ? 0 : width * 0.18
        keyboardLeading?.constant = side == "left" || side == "off" ? 0 : inset
        keyboardTrailing?.constant = side == "right" || side == "off" ? 0 : -inset
        if side != "off", let root = inputView {
            let r = SinglePawRail(coat: coat, side: side) { [weak self] action in
                guard let self = self else { return }
                if action == "full" { self.prefs.singlePaw = "off" } else { self.prefs.singlePaw = side == "left" ? "right" : "left" }
                self.applyLook()
            }
            r.translatesAutoresizingMaskIntoConstraints = false
            root.insertSubview(r, belowSubview: keyboardView)
            NSLayoutConstraint.activate([
                r.topAnchor.constraint(equalTo: whisker.bottomAnchor),
                r.bottomAnchor.constraint(equalTo: root.bottomAnchor),
                r.widthAnchor.constraint(equalToConstant: inset),
                side == "left" ? r.trailingAnchor.constraint(equalTo: root.trailingAnchor) : r.leadingAnchor.constraint(equalTo: root.leadingAnchor),
            ])
            rail = r
        }
    }

    func setLayout(_ id: LayoutId) {
        layoutId = id
        keyboardView.layout = Layouts.build(id, numberRow: prefs.numberRow, variant: variant, breed: breed, globe: needsInputModeSwitchKey)
        keyboardView.shift = id == .qwerty ? shift : .off
        heightConstraint?.constant = Self.barHeight + keyboardView.keysHeight
    }

    // MARK: Field

    private func configureForField() {
        let p = textDocumentProxy
        switch p.keyboardType ?? .default {
        case .numberPad, .decimalPad, .asciiCapableNumberPad: baseLayout = .numpad
        case .phonePad, .namePhonePad: baseLayout = .phone
        default: baseLayout = .qwerty
        }
        switch p.keyboardType ?? .default {
        case .emailAddress: variant = .email
        case .URL, .webSearch: variant = .url
        default: variant = .text
        }
        hunchesOn = prefs.hunches && baseLayout == .qwerty
        autoFixOn = hunchesOn && prefs.autoFix && variant == .text && p.autocorrectionType != .no
        learningOn = prefs.memory
        autoCapOn = prefs.autoCaps && variant == .text && p.autocapitalizationType != UITextAutocapitalizationType.none
        let action = p.returnKeyType ?? .default
        keyboardView.enterIsAction = ![.default, .continue].contains(action)
        keyboardView.enterLabel = {
            switch action {
            case .go: return "go"; case .google, .search, .yahoo: return "search"; case .send: return "send"
            case .next: return "next"; case .done: return "done"; case .join: return "join"; case .route: return "route"
            default: return "return"
            }
        }()
        keyboardView.enterSymbol = {
            switch action {
            case .go, .next: return "arrow.right"
            case .google, .search, .yahoo: return "magnifyingglass"
            case .send: return "paperplane"
            case .done: return "checkmark"
            default: return "return"
            }
        }()
    }

    private func resetTyping() {
        lastCorrection = nil; lastCommit = nil; lastAutoSpace = false; lastKeyWasSpace = false
        punctIndex = -1; glideWord = nil; glideAlternatives = []; currentHunches = []
        shift = .off; manualShift = false
    }

    // MARK: Text helpers

    var before: String { textDocumentProxy.documentContextBeforeInput ?? "" }
    var after: String { textDocumentProxy.documentContextAfterInput ?? "" }

    static func isWordChar(_ c: Character) -> Bool { c.isLetter || c.isNumber || c == "'" || c == "’" }

    /// The word right before the cursor (what's being typed).
    var currentWord: String {
        if let f = after.first, Self.isWordChar(f) { return "" }
        return String(before.reversed().prefix { Self.isWordChar($0) }.reversed())
    }

    func insert(_ s: String) { textDocumentProxy.insertText(s) }
    func deleteBack(_ n: Int) { for _ in 0..<max(0, n) { textDocumentProxy.deleteBackward() } }

    private func previousWord(skipCurrent: Bool) -> String? {
        var b = before
        if skipCurrent { b = String(b.dropLast(currentWord.count)) }
        b = String(b.reversed().drop { $0 == " " }.reversed())
        let w = String(b.reversed().prefix { Self.isWordChar($0) }.reversed())
        return w.contains { $0.isLetter } ? w : nil
    }

    func isBlocked(_ w: String) -> Bool { memory.isBlocked(w) || (prefs.cleanHunches && Offensive.contains(w)) }

    private func learn(_ previous: String?, _ word: String, boost: Int = 1) {
        guard learningOn, !word.isEmpty else { return }
        if dictionary?.contains(word) != true && word.contains(where: { $0.isLetter }) { memory.learnWord(word, boost: boost) }
        if let p = previous { memory.learnBigram(p, word) }
    }

    // MARK: KeyboardViewDelegate

    func keyDown(_ key: Key) { feedback.key(key.code) }

    func keyTapped(_ key: Key) {
        if handleKeyInMode(key) { return }
        let wasSpace = lastKeyWasSpace
        if key.code != Code.space && key.code != Code.shift { lastKeyWasSpace = false }
        switch key.code {
        case Code.shift: handleShift()
        case Code.delete: handleDelete()
        case Code.enter: handleEnter()
        case Code.space: handleSpace(wasSpace)
        case Code.symbols: setLayout(.symbols)
        case Code.symbolsMore: setLayout(.symbolsMore)
        case Code.alpha: setLayout(.qwerty); updateShift()
        case Code.emoji: openTool(.emoji)
        case Code.globe: advanceToNextInputMode()
        default: handleText(key.text)
        }
    }

    func popupChosen(_ key: Key, text: String) {
        if key.code == 44 && text == "☺" { openTool(.emoji) } else { handleText(text) }
    }

    /// Typing goes into the Translate or search bar instead of the app while one is open.
    var textSink: ((String) -> Void)?
    var deleteSink: (() -> Void)?
    var enterSink: (() -> Void)?

    private func handleKeyInMode(_ key: Key) -> Bool {
        guard textSink != nil else { return false }
        switch key.code {
        case Code.delete: deleteSink?()
        case Code.enter: enterSink?()
        case Code.space: textSink?(" ")
        case Code.shift: handleShift()
        case Code.symbols: setLayout(.symbols)
        case Code.symbolsMore: setLayout(.symbolsMore)
        case Code.alpha: setLayout(.qwerty)
        case Code.globe: advanceToNextInputMode()
        case Code.emoji: break
        default:
            var t = key.text
            if shift != .off && t.count == 1 && t.first!.isLetter { t = t.uppercased() }
            textSink?(t)
            if shift == .on { shift = .off; keyboardView.shift = shift }
        }
        return true
    }

    private func handleText(_ raw: String) {
        punctIndex = -1
        var text = raw
        if layoutId == .qwerty && shift != .off && text.count == 1 && text.first!.isLetter { text = text.uppercased() }
        let isWordChar = text.count == 1 && Self.isWordChar(text.first!)
        let isPunct = text.count == 1 && ".,!?;:".contains(text)
        let word = currentWord

        if isWordChar {
            if word.isEmpty { lastCommit = nil }
            insert(text)
            lastCorrection = nil
            lastAutoSpace = false
            glideWord = nil
            glideAlternatives = []
        } else if !word.isEmpty && text.count == 1 && ".,!?;:)\"".contains(text) {
            // Punctuation ends the word and may trigger Auto-fix.
            commitWord(word, separator: text)
            lastAutoSpace = false
        } else if isPunct && prefs.smartSpacing && lastAutoSpace && before.hasSuffix(" ") {
            // "word |" + "." -> "word. |"
            deleteBack(1)
            insert(text + " ")
            lastCorrection = nil
            lastCommit = nil
            lastAutoSpace = true
        } else {
            insert(text)
            lastAutoSpace = false
            lastCorrection = nil
            lastCommit = nil
        }
        manualShift = false
        if shift == .on { shift = .off }
        updateShift()
        updateHunches()
    }

    /// Ends [word] with [separator], applying Auto-fix if it's a typo.
    private func commitWord(_ word: String, separator: String) {
        var final = word
        if autoFixOn && glideWord == nil, let fix = dictionary?.autocorrect(word, extra: memory.learned, blocked: isBlocked) { final = fix }
        let prev = previousWord(skipCurrent: true)
        if final != word {
            deleteBack(word.count)
            insert(final)
        }
        insert(separator)
        lastCorrection = final != word ? Correction(original: word, corrected: final, separator: separator) : nil
        var options = [final]
        for h in currentHunches where !h.isShortcut && !options.contains(h.word) { options.append(h.word) }
        for g in glideAlternatives where !options.contains(g) { options.append(g) }
        if !options.contains(word) { options.append(word) }
        lastCommit = options.count > 1 ? Commit(options, 0, separator) : nil
        learn(prev, final)
        glideWord = nil
        glideAlternatives = []
    }

    private func handleSpace(_ wasSpace: Bool) {
        punctIndex = -1
        let word = currentWord
        if !word.isEmpty {
            commitWord(word, separator: " ")
        } else {
            let b = before
            if prefs.doubleSpace && wasSpace && variant == .text && b.count >= 2 && b.hasSuffix(" "),
               let c = b.dropLast().last, c.isLetter || c.isNumber || ")\"'".contains(c) {
                deleteBack(1)
                insert(". ")
                lastCorrection = nil
                lastCommit = nil
            } else {
                insert(" ")
            }
        }
        lastAutoSpace = false
        lastKeyWasSpace = true
        manualShift = false
        updateShift()
        updateHunches()
    }

    private func handleDelete() {
        punctIndex = -1
        lastAutoSpace = false
        let b = before
        if let c = lastCorrection {
            lastCorrection = nil
            // Backspace straight after Auto-fix brings back what you typed.
            if b.hasSuffix(c.corrected + c.separator) {
                deleteBack(c.corrected.count + c.separator.count)
                insert(c.original)
                if learningOn { memory.learnWord(c.original, boost: 2) }
                lastCommit = nil
                updateHunches()
                return
            }
        }
        if let g = glideWord, b.hasSuffix(g) {
            // Backspace right after Prowl removes the whole word.
            deleteBack(g.count)
            glideWord = nil
            glideAlternatives = []
        } else {
            textDocumentProxy.deleteBackward()
        }
        lastCommit = nil
        updateShift()
        updateHunches()
    }

    private func handleEnter() {
        lastAutoSpace = false; lastCorrection = nil; lastCommit = nil; punctIndex = -1
        glideWord = nil
        insert("\n")
        updateShift()
        updateHunches()
    }

    private func handleShift() {
        let now = CACurrentMediaTime()
        switch shift {
        case .off: shift = .on
        case .on: shift = now - lastShiftTap < 0.35 ? .locked : .off
        case .locked: shift = .off
        }
        lastShiftTap = now
        manualShift = true
        keyboardView.shift = shift
    }

    func updateShift() {
        guard layoutId == .qwerty else { return }
        if shift == .locked { keyboardView.shift = shift; return }
        if manualShift && shift == .on { return }
        var caps = false
        if autoCapOn && currentWord.isEmpty {
            let b = before
            switch textDocumentProxy.autocapitalizationType ?? .sentences {
            case .allCharacters: caps = true
            case .words: caps = b.isEmpty || b.last!.isWhitespace
            default:
                let trimmed = b.reversed().drop { $0 == " " }
                caps = b.isEmpty || trimmed.isEmpty || b.hasSuffix("\n") || (b.hasSuffix(" ") && ".!?".contains(trimmed.first!))
            }
        }
        shift = caps ? .on : .off
        keyboardView.shift = shift
    }

    private func applyShift(_ w: String) -> String {
        switch shift {
        case .locked: return w.uppercased()
        case .on: return w.prefix(1).uppercased() + w.dropFirst()
        case .off: return w
        }
    }

    func flicked(_ flick: Flick) {
        feedback.tick()
        manualShift = false
        switch flick {
        case .right:
            let word = currentWord
            let b = before
            if !word.isEmpty {
                commitWord(word, separator: " ")
                punctIndex = -1
            } else if lastAutoSpace && punctIndex < 0 && b.hasSuffix(" "), let c = b.dropLast().last, c.isLetter || c.isNumber {
                deleteBack(1)
                insert(Self.punctuation[0])
                punctIndex = 0
                lastCommit = nil
                lastCorrection = nil
            } else {
                insert(" ")
                punctIndex = -1
            }
            lastAutoSpace = true
        case .left: deleteWordBackward()
        case .down: cycle(1)
        case .up: cycle(-1)
        }
        updateShift()
        updateHunches()
    }

    private func cycle(_ dir: Int) {
        if punctIndex >= 0 {
            let cur = Self.punctuation[punctIndex]
            if before.hasSuffix(cur) {
                let n = (punctIndex + dir + Self.punctuation.count) % Self.punctuation.count
                deleteBack(cur.count)
                insert(Self.punctuation[n])
                punctIndex = n
            }
            return
        }
        let word = currentWord
        if !word.isEmpty {
            commitWord(word, separator: " ")
            lastAutoSpace = true
        }
        if let c = lastCommit {
            let cur = c.options[c.index] + c.separator
            if before.hasSuffix(cur) {
                let n = (c.index + dir + c.options.count) % c.options.count
                deleteBack(cur.count)
                insert(c.options[n] + c.separator)
                c.index = n
                lastCorrection = nil
                return
            }
        }
        if dir < 0 { handleShift() }
    }

    private func deleteWordBackward() {
        lastCorrection = nil; lastCommit = nil; punctIndex = -1; lastAutoSpace = false
        let b = Array(before)
        guard !b.isEmpty else { return }
        var i = b.count
        while i > 0 && b[i - 1].isWhitespace { i -= 1 }
        if i > 0 && Self.isWordChar(b[i - 1]) {
            while i > 0 && Self.isWordChar(b[i - 1]) { i -= 1 }
        } else if i > 0 {
            i -= 1
        }
        deleteBack(b.count - i)
    }

    func spaceCursor(_ steps: Int) {
        lastCorrection = nil; lastCommit = nil; lastAutoSpace = false; glideWord = nil
        for _ in 0..<abs(steps) {
            // Move by one whole character (emoji included), measured in UTF-16 units.
            let ch = steps > 0 ? after.first : before.last
            guard let c = ch else { break }
            textDocumentProxy.adjustTextPosition(byCharacterOffset: (steps > 0 ? 1 : -1) * c.utf16.count)
        }
        feedback.tick()
    }

    func spaceLongPressed() {
        feedback.tick()
        advanceToNextInputMode()
    }

    func deleteSwipe(words: Int) {
        // iPhone keyboards can't select text, so Scratch shows how much will go.
        whisker.message = words == 0 ? nil : (words == 1 ? "Erase 1 word" : "Erase \(words) words")
        if words > 0 { feedback.tick() }
    }

    func deleteSwipeEnded(words: Int) {
        whisker.message = nil
        guard words > 0 else { return }
        let b = Array(before)
        var i = b.count
        for _ in 0..<words {
            while i > 0 && b[i - 1].isWhitespace { i -= 1 }
            while i > 0 && !b[i - 1].isWhitespace { i -= 1 }
        }
        deleteBack(b.count - i)
        lastCorrection = nil; lastCommit = nil
        updateShift()
        updateHunches()
    }

    func glided(_ points: [CGPoint]) {
        guard let g = glide else { return }
        let words = g.decode(points, centers: keyboardView.letterCenters(), keyWidth: keyboardView.unitWidth, extra: memory.learned)
            .filter { !isBlocked($0) }
        guard !words.isEmpty else { return }
        if let sink = textSink {
            sink(applyShift(words[0]))
            return
        }
        let word = currentWord
        if !word.isEmpty {
            commitWord(word, separator: " ")
        } else if let c = before.last, !c.isWhitespace, !"([{\"'¿¡/@#".contains(c) {
            insert(" ")
        }
        let cased = words.map(applyShift)
        insert(cased[0])
        glideWord = cased[0]
        glideAlternatives = cased
        lastAutoSpace = false; lastCorrection = nil; lastCommit = nil; punctIndex = -1
        manualShift = false
        if shift == .on { shift = .off }
        updateShift()
        updateHunches()
    }

    // MARK: Hunches

    func updateHunches() {
        guard whisker.superview != nil else { return }
        if activePanel != nil || textSink != nil || !hunchesOn || layoutId != .qwerty && currentWord.isEmpty {
            currentHunches = []
            whisker.showTools()
            return
        }
        if let g = glideWord, before.hasSuffix(g), !glideAlternatives.isEmpty {
            currentHunches = glideAlternatives.prefix(3).map { Suggestion(word: $0) }
            whisker.show(hunches: currentHunches, autoIndex: -1)
            return
        }
        let typed = currentWord
        if !typed.isEmpty {
            var list: [Suggestion] = []
            if let t = prefs.tricks[typed.lowercased()] { list.append(Suggestion(word: t, isShortcut: true)) }
            var correction: String?
            if let d = dictionary {
                let found = d.suggest(typed, limit: 3, extra: memory.learned, blocked: isBlocked)
                correction = autoFixOn ? d.autocorrect(typed, extra: memory.learned, blocked: isBlocked) : nil
                if let c = correction {
                    list.append(Suggestion(word: c, isCorrection: true))
                    list.append(Suggestion(word: typed))
                    list += found.filter { $0.word.lowercased() != c.lowercased() && $0.word.lowercased() != typed.lowercased() }
                } else {
                    list += found
                    if !list.contains(where: { $0.word.lowercased() == typed.lowercased() }) { list.insert(Suggestion(word: typed), at: min(1, list.count)) }
                }
            } else {
                list.append(Suggestion(word: typed))
            }
            var seen = Set<String>()
            currentHunches = Array(list.filter { seen.insert($0.word).inserted }.prefix(3))
            let auto = correction.flatMap { c in currentHunches.firstIndex { $0.word == c } } ?? -1
            whisker.show(hunches: currentHunches, autoIndex: auto)
            return
        }
        if prefs.nextWord && learningOn, let prev = previousWord(skipCurrent: false), before.hasSuffix(" ") {
            let next = memory.predictNext(prev)
            if !next.isEmpty {
                currentHunches = next.map { Suggestion(word: applyShift($0)) }
                whisker.show(hunches: currentHunches, autoIndex: -1)
                return
            }
        }
        currentHunches = []
        whisker.showTools()
    }

    func hunchPicked(_ index: Int, _ s: Suggestion) {
        feedback.tick()
        let prev = previousWord(skipCurrent: true)
        if let g = glideWord, before.hasSuffix(g) {
            deleteBack(g.count)
            insert(s.word + " ")
            glideWord = nil
            glideAlternatives = []
        } else {
            let word = currentWord
            if word.isEmpty {
                if let c = before.last, !c.isWhitespace { insert(" ") }
            } else {
                deleteBack(word.count)
            }
            insert(s.word + " ")
        }
        if !s.isShortcut {
            var options = [s.word]
            for h in currentHunches where !h.isShortcut && !options.contains(h.word) { options.append(h.word) }
            lastCommit = Commit(options, 0, " ")
            learn(prev, s.word, boost: 2)
        } else {
            lastCommit = nil
        }
        lastCorrection = nil
        lastAutoSpace = true
        punctIndex = -1
        manualShift = false
        updateShift()
        updateHunches()
    }

    func hunchLongPressed(_ s: Suggestion) {
        guard !s.isShortcut else { return }
        memory.block(s.word)
        toast("“\(s.word)” removed from Hunches")
        updateHunches()
    }

    // MARK: Tools and panels (see KeyboardViewController+Panels.swift)

    var activePanel: Tool?
    var panelView: UIView?

    func showBar(_ bar: UIView?) {
        barHost.subviews.forEach { $0.removeFromSuperview() }
        if let b = bar {
            b.translatesAutoresizingMaskIntoConstraints = false
            barHost.addSubview(b)
            NSLayoutConstraint.activate([
                b.topAnchor.constraint(equalTo: barHost.topAnchor), b.bottomAnchor.constraint(equalTo: barHost.bottomAnchor),
                b.leadingAnchor.constraint(equalTo: barHost.leadingAnchor), b.trailingAnchor.constraint(equalTo: barHost.trailingAnchor),
            ])
        }
        barHost.isHidden = bar == nil
    }

    func showPanel(_ v: UIView?, tool: Tool?) {
        panelView?.removeFromSuperview()
        panelView = v
        activePanel = tool
        whisker.activeTool = tool
        if let v = v {
            v.translatesAutoresizingMaskIntoConstraints = false
            panelHost.addSubview(v)
            NSLayoutConstraint.activate([
                v.topAnchor.constraint(equalTo: panelHost.topAnchor), v.bottomAnchor.constraint(equalTo: panelHost.bottomAnchor),
                v.leadingAnchor.constraint(equalTo: panelHost.leadingAnchor), v.trailingAnchor.constraint(equalTo: panelHost.trailingAnchor),
            ])
            keyboardView.cancelGesture()
        }
        panelHost.isHidden = v == nil
        keyboardView.isHidden = v != nil
        updateHunches()
    }

    func closePanel() {
        showPanel(nil, tool: nil)
        updateShift()
    }

    // MARK: Toast

    func toast(_ text: String) {
        whisker.message = text
        DispatchQueue.main.asyncAfter(deadline: .now() + 1.8) { [weak self] in
            if self?.whisker.message == text { self?.whisker.message = nil }
        }
    }
}
