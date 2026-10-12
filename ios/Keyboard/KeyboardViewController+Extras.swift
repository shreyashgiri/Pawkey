import UIKit

/// Stickers, GIFs and Translate.
extension KeyboardViewController {
    func openExtra(_ tool: Tool) {
        switch tool {
        case .stickers:
            showPanel(StickerPanel(coat: coat, onSticker: { [weak self] st in self?.sendSticker(st) },
                                   onBack: { [weak self] in self?.closePanel() }), tool: .stickers)
        case .gifs:
            gifQuery = nil
            openGifs()
        case .translate:
            if translateBar != nil { stopTranslate() } else { startTranslate() }
        default:
            break
        }
    }

    // MARK: Stickers and GIFs

    /// iPhone keyboards can't insert images directly, so stickers and GIFs go on the
    /// clipboard to paste (this needs Full Access).
    func sendSticker(_ st: Sticker) {
        feedback.tick()
        guard hasFullAccess else { toast("Stickers need Allow Full Access"); return }
        guard let png = StickerArt.render(st, size: 512).pngData() else { return }
        UIPasteboard.general.setData(png, forPasteboardType: "public.png")
        toast("Sticker copied. Paste it into your chat.")
    }

    func openGifs() {
        let key = prefs.giphyKey
        showPanel(GifPanel(coat: coat, giphy: key.isEmpty ? nil : Giphy(key: key), fullAccess: hasFullAccess, query: gifQuery,
                           onGif: { [weak self] g in self?.sendGif(g) },
                           onSearch: { [weak self] in self?.startGifSearch() },
                           onSettings: { [weak self] in self?.openPanel(.settings) },
                           onBack: { [weak self] in self?.closePanel() }), tool: .gifs)
    }

    func startGifSearch() {
        startEntry(placeholder: "Search GIPHY, then return",
                   onEnter: { [weak self] q in self?.gifQuery = q.trimmingCharacters(in: .whitespaces); self?.openGifs() },
                   onClose: { [weak self] in self?.openGifs() })
    }

    func sendGif(_ gif: Gif) {
        feedback.tick()
        toast("Getting the GIF…")
        Giphy.fetch(gif.full) { [weak self] data in
            guard let self = self else { return }
            guard let d = data else { self.toast("Couldn't download that GIF"); return }
            UIPasteboard.general.setData(d, forPasteboardType: "com.compuserve.gif")
            self.toast("GIF copied. Paste it into your chat.")
        }
    }

    // MARK: Translate

    func startTranslate() {
        guard translateEngine.isAvailable else { toast("Translate needs iOS 18 or later"); return }
        translateEngine.attach(to: self)
        closePanel()
        let bar = TranslateBar(coat: coat,
                               onPick: { [weak self] source in self?.pickLanguage(source: source) },
                               onSwap: { [weak self] in
                                   guard let self = self else { return }
                                   let f = self.prefs.translateFrom
                                   self.prefs.translateFrom = self.prefs.translateTo
                                   self.prefs.translateTo = f
                                   self.translated = ""
                                   self.refreshTranslate()
                                   self.scheduleTranslate()
                               },
                               onInsert: { [weak self] in self?.insertTranslation() },
                               onClose: { [weak self] in self?.stopTranslate() })
        translateBar = bar
        translateText = ""
        translated = ""
        translateStatus = nil
        showBar(bar, height: 96)
        textSink = { [weak self] t in self?.translateText += t; self?.afterTranslateEdit() }
        deleteSink = { [weak self] in
            guard let self = self, !self.translateText.isEmpty else { return }
            self.translateText.removeLast()
            self.afterTranslateEdit()
        }
        enterSink = { [weak self] in self?.insertTranslation() }
        shift = autoCapOnForBuffer ? .on : .off
        keyboardView.shift = shift
        refreshTranslate()
        updateHunches()
    }

    private var autoCapOnForBuffer: Bool { prefs.autoCaps }

    func stopTranslate() {
        translateWork?.cancel()
        translateBar = nil
        textSink = nil
        deleteSink = nil
        enterSink = nil
        showBar(nil)
        closePanel()
        updateShift()
        updateHunches()
    }

    private func afterTranslateEdit() {
        refreshTranslate()
        scheduleTranslate()
    }

    private func scheduleTranslate() {
        translateWork?.cancel()
        let work = DispatchWorkItem { [weak self] in self?.runTranslate() }
        translateWork = work
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.3, execute: work)
    }

    private func runTranslate() {
        let text = translateText.trimmingCharacters(in: .whitespaces)
        guard !text.isEmpty else { translated = ""; refreshTranslate(); return }
        let to = prefs.translateTo
        translateEngine.translate(text, from: prefs.translateFrom, to: to) { [weak self] result, error in
            guard let self = self, self.translateBar != nil, self.translateText.trimmingCharacters(in: .whitespaces) == text else { return }
            self.translated = result ?? ""
            self.translateStatus = error == nil ? nil
                : "Download \(TranslateLanguages.name(to)) in the Translate app (or Settings → Apps → Translate) to use it here"
            self.refreshTranslate()
        }
    }

    private func refreshTranslate() {
        translateBar?.setLanguages(prefs.translateFrom, prefs.translateTo)
        translateBar?.show(source: translateText, result: translated, status: translateStatus)
    }

    private func insertTranslation() {
        let out = (translated.isEmpty ? translateText : translated).trimmingCharacters(in: .whitespaces)
        guard !out.isEmpty else { return }
        insert(out)
        translateText = ""
        translated = ""
        refreshTranslate()
    }

    private func pickLanguage(source: Bool) {
        let panel = LanguagePanel(coat: coat, title: source ? "Translate from" : "Translate into",
                                  selected: source ? prefs.translateFrom : prefs.translateTo) { [weak self] code in
            guard let self = self else { return }
            if source { self.prefs.translateFrom = code } else { self.prefs.translateTo = code }
            self.translated = ""
            self.translateStatus = nil
            self.showPanel(nil, tool: nil)
            self.refreshTranslate()
            self.scheduleTranslate()
        }
        showPanel(panel, tool: .translate)
    }
}
