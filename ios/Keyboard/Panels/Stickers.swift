import UIKit

enum Mood { case happy, laugh, love, shocked, sad, grumpy, sleepy, wink, cool, thanks, yum, miss }

struct Sticker { let id: String; let mood: Mood; let caption: String; let fur: UIColor; let dark: Bool }

/// The Whiskers sticker pack (the same art as on Android): round cat faces with
/// expressions, a caption, and a white die-cut outline.
enum StickerArt {
    private static let ginger = UIColor(hex: 0xF0A257), grey = UIColor(hex: 0xA8AEB8), black = UIColor(hex: 0x3A3A40)
    private static let cream = UIColor(hex: 0xF4DEC0), white = UIColor(hex: 0xF7F4EF), pink = UIColor(hex: 0xF6A7B5)
    private static let ink = UIColor(hex: 0x2B2420), lightInk = UIColor(hex: 0xF4EFE8)

    static let pack: [Sticker] = [
        Sticker(id: "hi", mood: .happy, caption: "Hi!", fur: ginger, dark: false),
        Sticker(id: "lol", mood: .laugh, caption: "LOL", fur: grey, dark: false),
        Sticker(id: "love", mood: .love, caption: "Love it", fur: cream, dark: false),
        Sticker(id: "omg", mood: .shocked, caption: "OMG", fur: black, dark: true),
        Sticker(id: "sorry", mood: .sad, caption: "Sorry", fur: grey, dark: false),
        Sticker(id: "grr", mood: .grumpy, caption: "Grr", fur: ginger, dark: false),
        Sticker(id: "night", mood: .sleepy, caption: "Good night", fur: black, dark: true),
        Sticker(id: "sure", mood: .wink, caption: "Sure", fur: cream, dark: false),
        Sticker(id: "cool", mood: .cool, caption: "Cool", fur: ginger, dark: false),
        Sticker(id: "thanks", mood: .thanks, caption: "Thanks", fur: white, dark: false),
        Sticker(id: "yum", mood: .yum, caption: "Yum", fur: grey, dark: false),
        Sticker(id: "miss", mood: .miss, caption: "Miss you", fur: white, dark: false),
    ]

    static func render(_ st: Sticker, size: CGFloat) -> UIImage {
        let fmt = UIGraphicsImageRendererFormat()
        fmt.scale = 1
        let canvas = CGSize(width: size, height: size)
        let art = UIGraphicsImageRenderer(size: canvas, format: fmt).image { ctx in draw(ctx.cgContext, st, size) }
        // Die-cut outline: stamp the silhouette in white around the art.
        return UIGraphicsImageRenderer(size: canvas, format: fmt).image { _ in
            let r = size * 0.025
            art.withTintColor(UIColor(white: 0, alpha: 0.14)).draw(at: CGPoint(x: 0, y: r * 1.6))
            let white = art.withTintColor(.white)
            for i in 0..<24 {
                let a = Double(i) * .pi * 2 / 24
                white.draw(at: CGPoint(x: CGFloat(cos(a)) * r, y: CGFloat(sin(a)) * r))
            }
            art.draw(at: .zero)
        }
    }

    private static func draw(_ c: CGContext, _ st: Sticker, _ s: CGFloat) {
        let cx = s / 2, cy = s * 0.43, r = s * 0.30
        let features = st.dark ? lightInk : ink
        c.setLineCap(.round)
        c.setLineJoin(.round)

        func tri(_ pts: [(CGFloat, CGFloat)]) {
            c.move(to: CGPoint(x: cx + pts[0].0 * r, y: cy + pts[0].1 * r))
            for p in pts.dropFirst() { c.addLine(to: CGPoint(x: cx + p.0 * r, y: cy + p.1 * r)) }
            c.closePath()
        }
        // Ears.
        tri([(-0.95, -0.25), (-0.80, -1.18), (-0.20, -0.86)]); tri([(0.95, -0.25), (0.80, -1.18), (0.20, -0.86)])
        c.setFillColor(st.fur.cgColor); c.setStrokeColor(ink.cgColor); c.setLineWidth(s * 0.018)
        c.drawPath(using: .fillStroke)
        tri([(-0.78, -0.45), (-0.72, -0.98), (-0.38, -0.80)]); tri([(0.78, -0.45), (0.72, -0.98), (0.38, -0.80)])
        c.setFillColor(pink.cgColor); c.fillPath()

        // Head.
        let head = CGRect(x: cx - r * 1.08, y: cy - r * 0.92, width: r * 2.16, height: r * 1.78)
        c.setFillColor(st.fur.cgColor); c.fillEllipse(in: head)
        c.setStrokeColor(ink.cgColor); c.strokeEllipse(in: head)

        // Cheeks.
        if [.happy, .love, .thanks, .wink, .miss, .yum, .laugh].contains(st.mood) {
            c.setFillColor(UIColor(hex: 0xFF7A8A, alpha: 0.4).cgColor)
            c.fillEllipse(in: CGRect(x: cx - r * 0.77, y: cy + r * 0.07, width: r * 0.3, height: r * 0.3))
            c.fillEllipse(in: CGRect(x: cx + r * 0.47, y: cy + r * 0.07, width: r * 0.3, height: r * 0.3))
        }

        // Eyes.
        let ex = r * 0.40, ey = cy - r * 0.10
        c.setStrokeColor(features.cgColor); c.setFillColor(features.cgColor); c.setLineWidth(s * 0.02)
        func dot(_ x: CGFloat, _ rad: CGFloat, dy: CGFloat = 0) { c.fillEllipse(in: CGRect(x: x - rad, y: ey + dy - rad, width: rad * 2, height: rad * 2)) }
        func arcEye(_ x: CGFloat, up: Bool) {
            c.addArc(center: CGPoint(x: x, y: ey + (up ? r * 0.06 : -r * 0.06)), radius: r * 0.14,
                     startAngle: up ? .pi * 1.15 : .pi * 0.15, endAngle: up ? .pi * 1.85 : .pi * 0.85, clockwise: false)
            c.strokePath()
        }
        func heart(_ x: CGFloat, _ y: CGFloat, _ size: CGFloat, _ color: UIColor) {
            c.setFillColor(color.cgColor)
            c.move(to: CGPoint(x: x, y: y + size * 0.9))
            c.addCurve(to: CGPoint(x: x, y: y - size * 0.35), control1: CGPoint(x: x - size * 1.4, y: y - size * 0.1), control2: CGPoint(x: x - size * 0.6, y: y - size))
            c.addCurve(to: CGPoint(x: x, y: y + size * 0.9), control1: CGPoint(x: x + size * 0.6, y: y - size), control2: CGPoint(x: x + size * 1.4, y: y - size * 0.1))
            c.fillPath()
            c.setFillColor(features.cgColor)
        }
        let red = UIColor(hex: 0xE8485F)
        switch st.mood {
        case .happy, .yum:
            dot(cx - ex, r * 0.10); dot(cx + ex, r * 0.10)
            c.setFillColor(UIColor.white.cgColor)
            c.fillEllipse(in: CGRect(x: cx - ex, y: ey - r * 0.075, width: r * 0.07, height: r * 0.07))
            c.fillEllipse(in: CGRect(x: cx + ex, y: ey - r * 0.075, width: r * 0.07, height: r * 0.07))
        case .laugh, .thanks: arcEye(cx - ex, up: true); arcEye(cx + ex, up: true)
        case .sleepy: arcEye(cx - ex, up: false); arcEye(cx + ex, up: false)
        case .wink: dot(cx - ex, r * 0.10); arcEye(cx + ex, up: true)
        case .love, .miss: heart(cx - ex, ey, r * 0.16, red); heart(cx + ex, ey, r * 0.16, red)
        case .shocked:
            c.setFillColor(UIColor.white.cgColor)
            for x in [cx - ex, cx + ex] {
                let e = CGRect(x: x - r * 0.17, y: ey - r * 0.17, width: r * 0.34, height: r * 0.34)
                c.fillEllipse(in: e); c.strokeEllipse(in: e)
            }
            c.setFillColor(ink.cgColor)
            dot(cx - ex, r * 0.06); dot(cx + ex, r * 0.06)
        case .sad:
            dot(cx - ex, r * 0.09, dy: r * 0.02); dot(cx + ex, r * 0.09, dy: r * 0.02)
            c.move(to: CGPoint(x: cx - ex - r * 0.15, y: ey - r * 0.12)); c.addLine(to: CGPoint(x: cx - ex + r * 0.10, y: ey - r * 0.22))
            c.move(to: CGPoint(x: cx + ex + r * 0.15, y: ey - r * 0.12)); c.addLine(to: CGPoint(x: cx + ex - r * 0.10, y: ey - r * 0.22))
            c.strokePath()
            c.setFillColor(UIColor(hex: 0x7FC4F2).cgColor)
            c.fillEllipse(in: CGRect(x: cx + r * 0.34, y: cy + r * 0.04, width: r * 0.16, height: r * 0.16))
        case .grumpy:
            dot(cx - ex, r * 0.08, dy: r * 0.03); dot(cx + ex, r * 0.08, dy: r * 0.03)
            c.move(to: CGPoint(x: cx - ex - r * 0.16, y: ey - r * 0.20)); c.addLine(to: CGPoint(x: cx - ex + r * 0.12, y: ey - r * 0.08))
            c.move(to: CGPoint(x: cx + ex + r * 0.16, y: ey - r * 0.20)); c.addLine(to: CGPoint(x: cx + ex - r * 0.12, y: ey - r * 0.08))
            c.strokePath()
        case .cool:
            c.setFillColor(UIColor(hex: 0x1E1E22).cgColor)
            c.addPath(CGPath(roundedRect: CGRect(x: cx - ex - r * 0.24, y: ey - r * 0.12, width: r * 0.46, height: r * 0.28), cornerWidth: r * 0.08, cornerHeight: r * 0.08, transform: nil))
            c.addPath(CGPath(roundedRect: CGRect(x: cx + ex - r * 0.22, y: ey - r * 0.12, width: r * 0.46, height: r * 0.28), cornerWidth: r * 0.08, cornerHeight: r * 0.08, transform: nil))
            c.fillPath()
            c.setStrokeColor(UIColor(hex: 0x1E1E22).cgColor)
            c.move(to: CGPoint(x: cx - ex + r * 0.2, y: ey - r * 0.06)); c.addLine(to: CGPoint(x: cx + ex - r * 0.2, y: ey - r * 0.06)); c.strokePath()
        }

        // Nose and mouth.
        let my = cy + r * 0.28
        c.setFillColor(UIColor(hex: 0xE07A8A).cgColor)
        c.move(to: CGPoint(x: cx - r * 0.08, y: my - r * 0.12)); c.addLine(to: CGPoint(x: cx + r * 0.08, y: my - r * 0.12)); c.addLine(to: CGPoint(x: cx, y: my - r * 0.03))
        c.fillPath()
        c.setStrokeColor(features.cgColor); c.setLineWidth(s * 0.016)
        func w() {
            c.addArc(center: CGPoint(x: cx - r * 0.08, y: my + r * 0.01), radius: r * 0.08, startAngle: 0, endAngle: .pi, clockwise: false)
            c.strokePath()
            c.addArc(center: CGPoint(x: cx + r * 0.08, y: my + r * 0.01), radius: r * 0.08, startAngle: 0, endAngle: .pi, clockwise: false)
            c.strokePath()
        }
        switch st.mood {
        case .laugh:
            c.setFillColor(UIColor(hex: 0x7A2E38).cgColor)
            c.move(to: CGPoint(x: cx - r * 0.22, y: my + r * 0.02))
            c.addArc(center: CGPoint(x: cx, y: my + r * 0.02), radius: r * 0.22, startAngle: .pi, endAngle: 0, clockwise: true)
            c.closePath(); c.fillPath()
        case .shocked:
            c.setFillColor(UIColor(hex: 0x7A2E38).cgColor)
            c.fillEllipse(in: CGRect(x: cx - r * 0.12, y: my, width: r * 0.24, height: r * 0.26))
        case .sad, .grumpy:
            c.addArc(center: CGPoint(x: cx, y: my + r * 0.22), radius: r * 0.16, startAngle: .pi * 1.2, endAngle: .pi * 1.8, clockwise: false)
            c.strokePath()
        case .yum:
            w()
            c.setFillColor(pink.cgColor)
            c.fillEllipse(in: CGRect(x: cx - r * 0.06, y: my + r * 0.04, width: r * 0.16, height: r * 0.18))
        default: w()
        }

        // Whiskers.
        c.setLineWidth(s * 0.010)
        for side in [-1.0, 1.0] as [CGFloat] {
            for k in -1...1 {
                c.move(to: CGPoint(x: cx + side * r * 0.70, y: cy + r * (0.22 + CGFloat(k) * 0.10)))
                c.addLine(to: CGPoint(x: cx + side * r * 1.25, y: cy + r * (0.14 + CGFloat(k) * 0.18)))
            }
        }
        c.strokePath()

        // Extras.
        switch st.mood {
        case .love, .miss: heart(cx + r * 1.05, cy - r * 0.95, r * 0.30, red)
        case .sleepy:
            let a: [NSAttributedString.Key: Any] = [.font: UIFont.boldSystemFont(ofSize: s * 0.08), .foregroundColor: ink]
            ("z" as NSString).draw(at: CGPoint(x: cx + r * 0.95, y: cy - r * 1.05), withAttributes: a)
            ("Z" as NSString).draw(at: CGPoint(x: cx + r * 1.2, y: cy - r * 1.4), withAttributes: [.font: UIFont.boldSystemFont(ofSize: s * 0.1), .foregroundColor: ink])
        case .shocked:
            c.setStrokeColor(ink.cgColor); c.setLineWidth(s * 0.014)
            c.move(to: CGPoint(x: cx - r * 1.35, y: cy - r * 0.9)); c.addLine(to: CGPoint(x: cx - r * 1.2, y: cy - r * 0.6))
            c.move(to: CGPoint(x: cx + r * 1.35, y: cy - r * 0.9)); c.addLine(to: CGPoint(x: cx + r * 1.2, y: cy - r * 0.6))
            c.strokePath()
        default: break
        }

        // Caption with an ink outline.
        let font = UIFont.systemFont(ofSize: s * 0.115, weight: .heavy)
        let outline: [NSAttributedString.Key: Any] = [.font: font, .strokeColor: ink, .strokeWidth: 6, .kern: s * 0.004]
        let fill: [NSAttributedString.Key: Any] = [.font: font, .foregroundColor: UIColor.white, .kern: s * 0.004]
        let sz = (st.caption as NSString).size(withAttributes: fill)
        let at = CGPoint(x: cx - sz.width / 2, y: s * 0.90 - sz.height * 0.8)
        UIGraphicsPushContext(c)
        (st.caption as NSString).draw(at: at, withAttributes: outline)
        (st.caption as NSString).draw(at: at, withAttributes: fill)
        UIGraphicsPopContext()
    }
}

final class StickerPanel: UIView {
    init(coat: Coat, onSticker: @escaping (Sticker) -> Void, onBack: @escaping () -> Void) {
        super.init(frame: .zero)
        backgroundColor = coat.background
        let cells: [UIView] = StickerArt.pack.map { st in
            let b = UIButton(type: .custom)
            b.setImage(StickerArt.render(st, size: 150), for: .normal)
            b.imageView?.contentMode = .scaleAspectFit
            b.accessibilityLabel = st.caption
            b.addAction(UIAction { _ in onSticker(st) }, for: .touchUpInside)
            b.heightAnchor.constraint(equalToConstant: 78).isActive = true
            return b
        }
        let grid = PanelKit.grid(cells, columns: 4, spacing: 4)
        grid.distribution = .fill
        let scroll = UIScrollView()
        scroll.showsVerticalScrollIndicator = false
        PanelKit.pin(grid, in: scroll)
        grid.widthAnchor.constraint(equalTo: scroll.frameLayoutGuide.widthAnchor).isActive = true
        let abc = PanelKit.button("ABC", coat: coat) { onBack() }
        abc.heightAnchor.constraint(equalToConstant: 42).isActive = true
        let v = UIStackView(arrangedSubviews: [PanelKit.header("Whiskers stickers", coat: coat), scroll, abc])
        v.axis = .vertical
        v.spacing = 4
        PanelKit.pin(v, in: self, insets: UIEdgeInsets(top: 0, left: 6, bottom: 4, right: 6))
    }

    required init?(coder: NSCoder) { fatalError() }
}
