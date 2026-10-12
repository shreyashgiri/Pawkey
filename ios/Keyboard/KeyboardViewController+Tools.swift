import UIKit

extension KeyboardViewController {
    func toolTapped(_ tool: Tool) {
        feedback.tick()
        openTool(tool)
    }

    /// Opens a tool from the Whisker bar or the Den.
    func openTool(_ tool: Tool) {
        let fromDen = activePanel == .den
        switch tool {
        case .hide: dismissKeyboard()
        case .numberRow:
            prefs.numberRow.toggle()
            setLayout(layoutId)
            if fromDen { openPanel(.den) }
        case .singlePaw:
            prefs.singlePaw = prefs.singlePaw == "off" ? "right" : "off"
            applyLook()
            if fromDen { openPanel(.den) }
        case .gestures:
            prefs.gestures = ["flicks": "prowl", "prowl": "off"][prefs.gestures] ?? "flicks"
            keyboardView.gestures = prefs.gestures
            if fromDen { openPanel(.den) }
            toast(["prowl": "Prowl on: swipe to type", "off": "Gestures off"][prefs.gestures] ?? "Flicks on")
        case .haptics:
            prefs.haptics.toggle()
            if fromDen { openPanel(.den) }
            toast(prefs.haptics ? "Haptics on" : "Haptics off")
        case .sound:
            let styles = Feedback.styles
            let next = styles[((styles.firstIndex(of: prefs.sound) ?? 0) + 1) % styles.count]
            prefs.sound = next
            feedback.preview(next)
            if fromDen { openPanel(.den) }
            toast("Key sound: \(Feedback.label(next))" + (hasFullAccess || next == "system" || next == "off" ? "" : " (needs Full Access)"))
        case .stretch: showStretch()
        default:
            if activePanel == tool { closePanel() } else { openPanel(tool) }
        }
    }

    func openPanel(_ tool: Tool) {
        if textSink != nil { endEntry() }
        switch tool {
        case .den:
            showPanel(DenPanel(coat: coat, onTool: { [weak self] t in self?.openTool(t) },
                               onPinsChanged: { [weak self] in
                                   guard let self = self else { return }
                                   self.whisker.tools = self.prefs.toolbar.compactMap { Tool(rawValue: $0) }
                               }), tool: .den)
        case .emoji:
            showPanel(EmojiPanel(coat: coat,
                                 onEmoji: { [weak self] e in self?.insert(e); self?.feedback.key(0) },
                                 onDelete: { [weak self] in self?.textDocumentProxy.deleteBackward() },
                                 onBack: { [weak self] in self?.closePanel() },
                                 onSearch: { [weak self] in self?.startEmojiSearch() }), tool: .emoji)
        case .stash:
            showPanel(StashPanel(coat: coat, store: StashStore(), fullAccess: hasFullAccess,
                                 onPaste: { [weak self] t in self?.insert(t); self?.closePanel() },
                                 onBack: { [weak self] in self?.closePanel() }), tool: .stash)
        case .groom:
            showPanel(GroomPanel(coat: coat) { [weak self] id in self?.groom(id) }, tool: .groom)
        case .coats:
            let items = (["system"] + Coat.all.map { $0.id }).map { id -> (id: String, name: String, coat: Coat, radius: CGFloat, pill: Bool, enterAccent: Bool) in
                let c = Coat.resolve(id, breed: breed, dark: isDark)
                return (id, id == "system" ? "Auto" : c.name, c, breed.radius, breed.enter == .pill, breed.enter != .function)
            }
            showPanel(SwatchPanel(title: "Coats", coat: coat, items: items, selected: prefs.coat, columns: 4) { [weak self] id in
                self?.prefs.coat = id
                self?.applyLook()
                self?.openPanel(.coats)
            }, tool: .coats)
        case .breeds:
            let items = Breed.allCases.map { b -> (id: String, name: String, coat: Coat, radius: CGFloat, pill: Bool, enterAccent: Bool) in
                (b.rawValue, b.label, isDark ? b.darkCoat : b.lightCoat, b.radius, b.enter == .pill, b.enter != .function)
            }
            showPanel(SwatchPanel(title: "Breeds", coat: coat, items: items, selected: prefs.breed.rawValue, columns: 2) { [weak self] id in
                self?.prefs.breed = Breed(rawValue: id) ?? .pawkeys
                self?.applyLook()
                self?.openPanel(.breeds)
            }, tool: .breeds)
        case .settings:
            showPanel(SettingsPanel(coat: coat, rows: settingsRows(), onBack: { [weak self] in self?.closePanel() }), tool: .settings)
        case .tricks:
            showPanel(TricksPanel(coat: coat, onAdd: { [weak self] in self?.addTrick() },
                                  onChanged: { [weak self] in self?.openPanel(.tricks) },
                                  onBack: { [weak self] in self?.closePanel() }), tool: .tricks)
        default:
            openExtra(tool)
        }
    }

    // MARK: Entry bar (typing goes into the bar instead of the app)

    func startEntry(placeholder: String, symbol: String = "magnifyingglass", showsResults: Bool = false, initial: String = "",
                    onChange: ((String, EntryBar) -> Void)? = nil, onEnter: @escaping (String) -> Void, onClose: @escaping () -> Void) {
        closePanel()
        let bar = EntryBar(coat: coat, placeholder: placeholder, symbol: symbol, showsResults: showsResults) { [weak self] in
            self?.endEntry()
            onClose()
        }
        bar.text = initial
        showBar(bar)
        textSink = { [weak bar] t in guard let bar = bar else { return }; bar.text += t; onChange?(bar.text, bar) }
        deleteSink = { [weak bar] in guard let bar = bar else { return }; bar.text = String(bar.text.dropLast()); onChange?(bar.text, bar) }
        enterSink = { [weak self, weak bar] in
            let t = bar?.text ?? ""
            self?.endEntry()
            onEnter(t)
        }
        shift = .off
        keyboardView.shift = .off
        updateHunches()
    }

    func endEntry() {
        textSink = nil
        deleteSink = nil
        enterSink = nil
        showBar(nil)
        updateShift()
        updateHunches()
    }

    func startEmojiSearch() {
        startEntry(placeholder: "Search emoji", showsResults: true,
                   onChange: { [weak self] q, bar in
                       bar.showResults(q.trimmingCharacters(in: .whitespaces).isEmpty ? [] : EmojiSearch.search(q)) { e in
                           self?.insert(e)
                           self?.feedback.key(0)
                       }
                   },
                   onEnter: { [weak self] _ in self?.openPanel(.emoji) },
                   onClose: { [weak self] in self?.openPanel(.emoji) })
    }

    func addTrick() {
        startEntry(placeholder: "Type: code = phrase, then return", symbol: "text.badge.plus",
                   onEnter: { [weak self] t in
                       let parts = t.split(separator: "=", maxSplits: 1).map { $0.trimmingCharacters(in: .whitespaces) }
                       if parts.count == 2, !parts[0].isEmpty, !parts[1].isEmpty, !parts[0].contains(" ") {
                           var m = Prefs.shared.tricks
                           m[parts[0].lowercased()] = parts[1]
                           Prefs.shared.tricks = m
                       } else {
                           self?.toast("Use the form: omw = On my way!")
                       }
                       self?.openPanel(.tricks)
                   },
                   onClose: { [weak self] in self?.openPanel(.tricks) })
    }

    // MARK: Stretch

    func showStretch() {
        closePanel()
        var value = prefs.height
        showBar(StretchBar(coat: coat, start: value, onChange: { [weak self] v in
            value = v
            guard let self = self else { return }
            self.keyboardView.heightScale = CGFloat(v) / 100
            self.setLayout(self.layoutId)
        }, onDone: { [weak self] in
            self?.prefs.height = value
            self?.showBar(nil)
            self?.applyLook()
        }))
    }

    // MARK: Groom

    func groom(_ id: String) {
        feedback.tick()
        let p = textDocumentProxy
        switch id {
        case "left": spaceCursor(-1)
        case "right": spaceCursor(1)
        case "wordLeft":
            let b = Array(before)
            var i = b.count
            while i > 0 && !Self.isWordChar(b[i - 1]) { i -= 1 }
            while i > 0 && Self.isWordChar(b[i - 1]) { i -= 1 }
            p.adjustTextPosition(byCharacterOffset: -String(b[i...]).utf16.count)
        case "wordRight":
            let a = Array(after)
            var i = 0
            while i < a.count && !Self.isWordChar(a[i]) { i += 1 }
            while i < a.count && Self.isWordChar(a[i]) { i += 1 }
            p.adjustTextPosition(byCharacterOffset: String(a[..<i]).utf16.count)
        case "lineStart":
            let b = before
            let tail = b.split(separator: "\n", omittingEmptySubsequences: false).last.map(String.init) ?? b
            p.adjustTextPosition(byCharacterOffset: -tail.utf16.count)
        case "lineEnd":
            let a = after
            let head = a.split(separator: "\n", omittingEmptySubsequences: false).first.map(String.init) ?? a
            p.adjustTextPosition(byCharacterOffset: head.utf16.count)
        case "copy", "cut":
            guard hasFullAccess else { toast("Copy needs Allow Full Access"); return }
            guard let sel = p.selectedText, !sel.isEmpty else { toast("Select text in the app first"); return }
            UIPasteboard.general.string = sel
            if id == "cut" { p.deleteBackward() }
            toast(id == "cut" ? "Cut" : "Copied")
        case "paste":
            guard hasFullAccess else { toast("Paste needs Allow Full Access"); return }
            if let s = UIPasteboard.general.string, !s.isEmpty { insert(s) } else { toast("Nothing to paste") }
        case "deleteWord":
            flicked(.left)
        case "delete":
            p.deleteBackward()
        case "abc":
            closePanel()
        default:
            break
        }
    }

    // MARK: Settings

    func settingsRows() -> [SettingsPanel.Row] {
        let reopen: () -> Void = { [weak self] in self?.applyLook(); self?.openPanel(.settings) }
        return [
            .note(hasFullAccess ? "Full Access is on: sounds, Stash, stickers, GIFs and paste all work."
                  : "Turn on Allow Full Access (Settings → General → Keyboard → Keyboards → PawKeys) for sounds, Stash, stickers, GIFs and paste."),
            .section("Look"),
            .choice("Breed", prefs.breed.label, { [weak self] in self?.openPanel(.breeds) }),
            .choice("Coat", prefs.coat == "system" ? "Auto" : Coat.resolve(prefs.coat, breed: breed, dark: isDark).name, { [weak self] in self?.openPanel(.coats) }),
            .toggle("Key outlines", prefs.keyOutlines, { [weak self] v in self?.prefs.keyOutlines = v; reopen() }),
            .toggle("Number row", prefs.numberRow, { [weak self] v in self?.prefs.numberRow = v; reopen() }),
            .toggle("Corner hints", prefs.hints, { [weak self] v in self?.prefs.hints = v; reopen() }),
            .choice("Stretch", "\(prefs.height)%", { [weak self] in self?.showStretch() }),
            .choice("Single paw", ["left": "Left paw", "right": "Right paw"][prefs.singlePaw] ?? "Off", { [weak self] in
                guard let self = self else { return }
                self.prefs.singlePaw = ["off": "right", "right": "left"][self.prefs.singlePaw] ?? "off"
                reopen()
            }),
            .section("Typing"),
            .toggle("Hunches", prefs.hunches, { [weak self] v in self?.prefs.hunches = v }),
            .toggle("Auto-fix", prefs.autoFix, { [weak self] v in self?.prefs.autoFix = v }),
            .toggle("Next-word hunches", prefs.nextWord, { [weak self] v in self?.prefs.nextWord = v }),
            .toggle("Memory (learn new words)", prefs.memory, { [weak self] v in self?.prefs.memory = v }),
            .toggle("Clean hunches", prefs.cleanHunches, { [weak self] v in self?.prefs.cleanHunches = v }),
            .toggle("Auto capitals", prefs.autoCaps, { [weak self] v in self?.prefs.autoCaps = v }),
            .toggle("Double-space full stop", prefs.doubleSpace, { [weak self] v in self?.prefs.doubleSpace = v }),
            .toggle("Smart spacing", prefs.smartSpacing, { [weak self] v in self?.prefs.smartSpacing = v }),
            .choice("Tricks", "\(prefs.tricks.count)", { [weak self] in self?.openPanel(.tricks) }),
            .choice("Clear memory", "Clear", { [weak self] in self?.memory.clear(); self?.toast("Memory cleared") }),
            .section("Gestures"),
            .choice("Swipe on letters", ["prowl": "Prowl", "off": "Off"][prefs.gestures] ?? "Flicks", { [weak self] in
                guard let self = self else { return }
                self.prefs.gestures = ["flicks": "prowl", "prowl": "off"][self.prefs.gestures] ?? "flicks"
                reopen()
            }),
            .toggle("Scoot (spacebar cursor)", prefs.scoot, { [weak self] v in self?.prefs.scoot = v; self?.applyLook() }),
            .toggle("Scratch (swipe-left erase)", prefs.scratch, { [weak self] v in self?.prefs.scratch = v; self?.applyLook() }),
            .toggle("Running cat", prefs.runningCat, { [weak self] v in self?.prefs.runningCat = v; self?.applyLook() }),
            .section("Feel"),
            .choice("Key sound", Feedback.label(prefs.sound), { [weak self] in
                guard let self = self else { return }
                let s = Feedback.styles
                self.prefs.sound = s[((s.firstIndex(of: self.prefs.sound) ?? 0) + 1) % s.count]
                self.feedback.preview(self.prefs.sound)
                reopen()
            }),
            .toggle("Haptics", prefs.haptics, { [weak self] v in self?.prefs.haptics = v }),
            .toggle("Key pop-up", prefs.keyPopup, { [weak self] v in self?.prefs.keyPopup = v; self?.applyLook() }),
            .section("GIFs"),
            .choice("GIPHY key", prefs.giphyKey.isEmpty ? "Not set" : "Set", { [weak self] in self?.editGiphyKey() }),
            .note("Create a free key at developers.giphy.com, copy it, then tap GIPHY key → Paste."),
        ]
    }

    func editGiphyKey() {
        if hasFullAccess, let s = UIPasteboard.general.string?.trimmingCharacters(in: .whitespacesAndNewlines), s.count >= 20, !s.contains(" ") {
            prefs.giphyKey = s
            toast("GIPHY key pasted")
            openPanel(.settings)
            return
        }
        startEntry(placeholder: "Type your GIPHY key, then return", symbol: "key", initial: prefs.giphyKey,
                   onEnter: { [weak self] t in self?.prefs.giphyKey = t; self?.openPanel(.settings) },
                   onClose: { [weak self] in self?.openPanel(.settings) })
    }

    /// Stickers, GIFs and Translate (KeyboardViewController+Extras.swift).
    func openExtra(_ tool: Tool) {
        toast("\(tool.label) is coming in the next update")
    }
}
