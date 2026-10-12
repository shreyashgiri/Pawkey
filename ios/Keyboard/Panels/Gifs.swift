import ImageIO
import UIKit

struct Gif { let id: String; let title: String; let preview: URL; let full: URL }

/// GIPHY's API with the user's own key. Network access needs Full Access on iPhone.
final class Giphy {
    let key: String
    init(key: String) { self.key = key }

    func list(query: String?, done: @escaping ([Gif]?, String?) -> Void) {
        var c = URLComponents(string: "https://api.giphy.com/v1/gifs/" + ((query ?? "").isEmpty ? "trending" : "search"))!
        c.queryItems = [URLQueryItem(name: "api_key", value: key), URLQueryItem(name: "limit", value: "30"), URLQueryItem(name: "rating", value: "pg-13")]
        if let q = query, !q.isEmpty { c.queryItems?.append(URLQueryItem(name: "q", value: q)) }
        URLSession.shared.dataTask(with: c.url!) { data, resp, err in
            let status = (resp as? HTTPURLResponse)?.statusCode ?? 0
            let result: ([Gif]?, String?)
            if let data = data, status == 200 { result = (Giphy.parse(data), nil) }
            else if status == 401 || status == 403 { result = (nil, "GIPHY didn't accept the key") }
            else { result = (nil, err == nil ? "GIPHY error \(status)" : "Couldn't reach GIPHY") }
            DispatchQueue.main.async { done(result.0, result.1) }
        }.resume()
    }

    static func parse(_ data: Data) -> [Gif] {
        guard let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any], let items = json["data"] as? [[String: Any]] else { return [] }
        return items.compactMap { o in
            guard let images = o["images"] as? [String: Any],
                  let small = (images["fixed_width_small"] ?? images["fixed_width"]) as? [String: Any],
                  let p = (small["url"] as? String).flatMap(URL.init(string:)) else { return nil }
            let fullObj = (images["downsized"] ?? images["original"]) as? [String: Any]
            let f = (fullObj?["url"] as? String).flatMap(URL.init(string:)) ?? p
            return Gif(id: o["id"] as? String ?? "", title: o["title"] as? String ?? "GIF", preview: p, full: f)
        }
    }

    static func fetch(_ url: URL, done: @escaping (Data?) -> Void) {
        URLSession.shared.dataTask(with: url) { data, _, _ in DispatchQueue.main.async { done(data) } }.resume()
    }

    /// Decodes a GIF into an animated UIImage.
    static func animated(_ data: Data) -> UIImage? {
        guard let src = CGImageSourceCreateWithData(data as CFData, nil) else { return UIImage(data: data) }
        let n = CGImageSourceGetCount(src)
        if n <= 1 { return UIImage(data: data) }
        var frames: [UIImage] = []
        var duration: Double = 0
        for i in 0..<min(n, 60) {
            guard let cg = CGImageSourceCreateImageAtIndex(src, i, nil) else { continue }
            frames.append(UIImage(cgImage: cg))
            let props = CGImageSourceCopyPropertiesAtIndex(src, i, nil) as? [CFString: Any]
            let gif = props?[kCGImagePropertyGIFDictionary] as? [CFString: Any]
            duration += (gif?[kCGImagePropertyGIFUnclampedDelayTime] as? Double) ?? (gif?[kCGImagePropertyGIFDelayTime] as? Double) ?? 0.1
        }
        return UIImage.animatedImage(with: frames, duration: max(0.2, duration))
    }
}

final class GifPanel: UIView {
    private let coat: Coat
    private let content = UIStackView()

    init(coat: Coat, giphy: Giphy?, fullAccess: Bool, query: String?, onGif: @escaping (Gif) -> Void,
         onSearch: @escaping () -> Void, onSettings: @escaping () -> Void, onBack: @escaping () -> Void) {
        self.coat = coat
        super.init(frame: .zero)
        backgroundColor = coat.background
        content.axis = .vertical
        content.spacing = 6
        let scroll = UIScrollView()
        scroll.showsVerticalScrollIndicator = false
        PanelKit.pin(content, in: scroll)
        content.widthAnchor.constraint(equalTo: scroll.frameLayoutGuide.widthAnchor).isActive = true
        let abc = PanelKit.button("ABC", coat: coat) { onBack() }
        let credit = PanelKit.label("Powered by GIPHY", coat: coat, size: 11, weight: .regular, color: coat.hintText)
        credit.textAlignment = .center
        let bottom = UIStackView(arrangedSubviews: [abc, credit])
        bottom.distribution = .fillEqually
        bottom.heightAnchor.constraint(equalToConstant: 42).isActive = true
        let title = (query ?? "").isEmpty ? "Trending GIFs" : "GIFs: \(query!)"
        let v = UIStackView(arrangedSubviews: [PanelKit.header(title, coat: coat, action: giphy == nil ? nil : "Search", onAction: onSearch), scroll, bottom])
        v.axis = .vertical
        v.spacing = 4
        PanelKit.pin(v, in: self, insets: UIEdgeInsets(top: 0, left: 6, bottom: 4, right: 6))

        if !fullAccess {
            message("GIFs need Allow Full Access:\nSettings → General → Keyboard → Keyboards → PawKeys")
        } else if let g = giphy {
            message("Loading…")
            g.list(query: query) { [weak self] gifs, err in
                guard let self = self else { return }
                if let e = err { self.message(e) }
                else if let gifs = gifs, !gifs.isEmpty { self.grid(gifs, onGif) }
                else { self.message("No GIFs found") }
            }
        } else {
            message("Add your free GIPHY key to see GIFs:\nPaw → Settings → GIPHY key", action: "Open settings", onAction: onSettings)
        }
    }

    required init?(coder: NSCoder) { fatalError() }

    private func message(_ text: String, action: String? = nil, onAction: @escaping () -> Void = {}) {
        content.arrangedSubviews.forEach { $0.removeFromSuperview() }
        let l = PanelKit.label(text, coat: coat, size: 14, weight: .regular, color: coat.hintText)
        l.numberOfLines = 0
        l.textAlignment = .center
        let wrap = UIView()
        PanelKit.pin(l, in: wrap, insets: UIEdgeInsets(top: 24, left: 8, bottom: 8, right: 8))
        content.addArrangedSubview(wrap)
        if let a = action {
            let b = PanelKit.button(a, coat: coat, background: coat.accent, tint: coat.onAccent, action: onAction)
            b.heightAnchor.constraint(equalToConstant: 40).isActive = true
            content.addArrangedSubview(b)
        }
    }

    private func grid(_ gifs: [Gif], _ onGif: @escaping (Gif) -> Void) {
        content.arrangedSubviews.forEach { $0.removeFromSuperview() }
        let cells: [UIView] = gifs.map { gif in
            let iv = UIImageView()
            iv.contentMode = .scaleAspectFill
            iv.clipsToBounds = true
            iv.layer.cornerRadius = 12
            iv.backgroundColor = coat.key
            iv.isUserInteractionEnabled = true
            iv.accessibilityLabel = gif.title
            iv.heightAnchor.constraint(equalToConstant: 104).isActive = true
            iv.addGestureRecognizer(GifTap(gif: gif, onGif))
            Giphy.fetch(gif.preview) { [weak iv] d in if let d = d { iv?.image = Giphy.animated(d) } }
            return iv
        }
        let g = PanelKit.grid(cells, columns: 2, spacing: 6)
        g.distribution = .fill
        content.addArrangedSubview(g)
    }
}

private final class GifTap: UITapGestureRecognizer {
    let gif: Gif
    let handler: (Gif) -> Void
    init(gif: Gif, _ handler: @escaping (Gif) -> Void) {
        self.gif = gif
        self.handler = handler
        super.init(target: nil, action: nil)
        addTarget(self, action: #selector(fire))
    }
    @objc private func fire() { handler(gif) }
}
