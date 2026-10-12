import SwiftUI
import UIKit
#if canImport(Translation)
import Translation
#endif

/// Languages Apple's on-device translation offers.
enum TranslateLanguages {
    static let all: [(String, String)] = [
        ("en", "English"), ("hi", "Hindi"), ("es", "Spanish"), ("fr", "French"), ("de", "German"), ("it", "Italian"),
        ("pt", "Portuguese"), ("nl", "Dutch"), ("pl", "Polish"), ("ru", "Russian"), ("uk", "Ukrainian"), ("tr", "Turkish"),
        ("ar", "Arabic"), ("zh-Hans", "Chinese (Simplified)"), ("zh-Hant", "Chinese (Traditional)"), ("ja", "Japanese"),
        ("ko", "Korean"), ("id", "Indonesian"), ("th", "Thai"), ("vi", "Vietnamese"),
    ]
    static func name(_ code: String) -> String { all.first { $0.0 == code }?.1 ?? code }
}

#if canImport(Translation)
@available(iOS 18.0, *)
final class TranslationModel: ObservableObject {
    @Published var configuration: TranslationSession.Configuration?
    private var pending: [(String, (String?, String?) -> Void)] = []

    func request(_ text: String, from: String, to: String, done: @escaping (String?, String?) -> Void) {
        pending.append((text, done))
        let src = Locale.Language(identifier: from), dst = Locale.Language(identifier: to)
        if configuration?.source == src && configuration?.target == dst {
            configuration?.invalidate()
        } else {
            configuration = TranslationSession.Configuration(source: src, target: dst)
        }
    }

    @MainActor
    func run(_ session: TranslationSession) async {
        let jobs = pending
        pending = []
        for (text, done) in jobs {
            do {
                let r = try await session.translate(text)
                done(r.targetText, nil)
            } catch {
                done(nil, error.localizedDescription)
            }
        }
    }
}

@available(iOS 18.0, *)
struct TranslationHostView: View {
    @ObservedObject var model: TranslationModel
    var body: some View {
        Color.clear
            .translationTask(model.configuration) { session in
                await model.run(session)
            }
    }
}
#endif

/// Wraps Apple's Translation framework (iOS 18+). Its sessions only come from a
/// SwiftUI view, so a 1×1 invisible host view lives inside the keyboard.
final class TranslateEngine {
    private var host: UIViewController?
    private var model: AnyObject?

    var isAvailable: Bool {
        if #available(iOS 18.0, *) { return true }
        return false
    }

    func attach(to parent: UIViewController) {
        guard host == nil else { return }
        #if canImport(Translation)
        if #available(iOS 18.0, *) {
            let m = TranslationModel()
            let h = UIHostingController(rootView: TranslationHostView(model: m))
            h.view.backgroundColor = .clear
            h.view.isUserInteractionEnabled = false
            h.view.frame = CGRect(x: 0, y: 0, width: 1, height: 1)
            parent.addChild(h)
            parent.view.addSubview(h.view)
            h.didMove(toParent: parent)
            host = h
            model = m
        }
        #endif
    }

    func translate(_ text: String, from: String, to: String, done: @escaping (String?, String?) -> Void) {
        #if canImport(Translation)
        if #available(iOS 18.0, *), let m = model as? TranslationModel {
            m.request(text, from: from, to: to) { r, e in DispatchQueue.main.async { done(r, e) } }
            return
        }
        #endif
        done(nil, "Translate needs iOS 18 or later")
    }
}

/// Replaces the Whisker bar while translating.
final class TranslateBar: UIView {
    private let from = UIButton(type: .system)
    private let to = UIButton(type: .system)
    private let source = UILabel()
    private let result = UILabel()
    private let coat: Coat

    init(coat: Coat, onPick: @escaping (Bool) -> Void, onSwap: @escaping () -> Void, onInsert: @escaping () -> Void, onClose: @escaping () -> Void) {
        self.coat = coat
        super.init(frame: .zero)
        backgroundColor = coat.background
        for (b, src) in [(from, true), (to, false)] {
            var cfg = UIButton.Configuration.filled()
            cfg.baseBackgroundColor = coat.functionKey
            cfg.baseForegroundColor = coat.keyText
            cfg.cornerStyle = .capsule
            cfg.contentInsets = NSDirectionalEdgeInsets(top: 4, leading: 12, bottom: 4, trailing: 12)
            b.configuration = cfg
            b.addAction(UIAction { _ in onPick(src) }, for: .touchUpInside)
        }
        let swap = PanelKit.button(nil, symbol: "arrow.left.arrow.right", coat: coat, background: coat.background, tint: coat.stripText) { onSwap() }
        let close = PanelKit.button(nil, symbol: "xmark", coat: coat, background: coat.background, tint: coat.stripText) { onClose() }
        let top = UIStackView(arrangedSubviews: [from, swap, to, UIView(), close])
        top.spacing = 6
        top.alignment = .center
        swap.widthAnchor.constraint(equalToConstant: 40).isActive = true
        close.widthAnchor.constraint(equalToConstant: 40).isActive = true

        source.font = .systemFont(ofSize: 12.5)
        source.textColor = coat.hintText
        source.lineBreakMode = .byTruncatingHead
        result.font = .systemFont(ofSize: 15, weight: .medium)
        result.lineBreakMode = .byTruncatingHead
        let texts = UIStackView(arrangedSubviews: [source, result])
        texts.axis = .vertical
        let insert = PanelKit.button("Insert", coat: coat, background: coat.accent, tint: coat.onAccent, size: 14) { onInsert() }
        insert.widthAnchor.constraint(equalToConstant: 76).isActive = true
        let bottomRow = UIStackView(arrangedSubviews: [texts, insert])
        bottomRow.spacing = 8
        bottomRow.alignment = .center
        let box = UIView()
        box.backgroundColor = coat.key
        box.layer.cornerRadius = 12
        PanelKit.pin(bottomRow, in: box, insets: UIEdgeInsets(top: 4, left: 12, bottom: 4, right: 4))

        let v = UIStackView(arrangedSubviews: [top, box])
        v.axis = .vertical
        v.spacing = 4
        top.heightAnchor.constraint(equalToConstant: 38).isActive = true
        PanelKit.pin(v, in: self, insets: UIEdgeInsets(top: 4, left: 8, bottom: 4, right: 8))
    }

    required init?(coder: NSCoder) { fatalError() }

    func setLanguages(_ f: String, _ t: String) {
        from.configuration?.title = TranslateLanguages.name(f) + " ▾"
        to.configuration?.title = TranslateLanguages.name(t) + " ▾"
    }

    func show(source s: String, result r: String, status: String?) {
        source.text = s.isEmpty ? "Type to translate" : s
        result.text = status ?? (r.isEmpty ? " " : r)
        result.textColor = status != nil ? coat.hintText : coat.keyText
    }
}

/// Language picker shown in place of the keys.
final class LanguagePanel: UIView {
    init(coat: Coat, title: String, selected: String, onPick: @escaping (String) -> Void) {
        super.init(frame: .zero)
        backgroundColor = coat.background
        let buttons: [UIView] = TranslateLanguages.all.map { code, name in
            let chosen = code == selected
            let b = PanelKit.button(name, coat: coat, background: chosen ? coat.accent : coat.key, tint: chosen ? coat.onAccent : coat.keyText,
                                    size: 13.5, weight: chosen ? .semibold : .regular) { onPick(code) }
            b.heightAnchor.constraint(equalToConstant: 40).isActive = true
            return b
        }
        let grid = PanelKit.grid(buttons, columns: 3, spacing: 6)
        grid.distribution = .fill
        let scroll = UIScrollView()
        PanelKit.pin(grid, in: scroll)
        grid.widthAnchor.constraint(equalTo: scroll.frameLayoutGuide.widthAnchor).isActive = true
        let v = UIStackView(arrangedSubviews: [PanelKit.header(title, coat: coat), scroll])
        v.axis = .vertical
        PanelKit.pin(v, in: self, insets: UIEdgeInsets(top: 0, left: 6, bottom: 4, right: 6))
    }

    required init?(coder: NSCoder) { fatalError() }
}
