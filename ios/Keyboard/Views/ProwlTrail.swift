import UIKit

/// The Prowl trail: a smooth, tapering curve following the finger, with a small cat
/// galloping after the fingertip. Drawn in code in the coat's accent colour.
final class ProwlTrail {
    private var points: [CGPoint] = []
    private var cat = CGPoint.zero
    private var facingRight = true
    private var tilt: CGFloat = 0
    private var phase: CGFloat = 0
    private var fadeStart: CFTimeInterval = 0
    var showCat = true

    private let catSize: CGFloat = 34
    private let catLift: CGFloat = 30
    private let fadeDuration: CFTimeInterval = 0.26

    var isActive: Bool { !points.isEmpty }

    func start(_ p: CGPoint) {
        points = [p]
        fadeStart = 0
        cat = CGPoint(x: p.x, y: p.y - catLift)
        phase = 0
    }

    func add(_ p: CGPoint) {
        points.append(p)
        chase(0.35)
    }

    func release() { if !points.isEmpty { fadeStart = CACurrentMediaTime() } }
    func clear() { points.removeAll(); fadeStart = 0 }

    private func chase(_ amount: CGFloat) {
        guard let target = points.last else { return }
        let dx = (target.x - cat.x) * amount
        let dy = (target.y - catLift - cat.y) * amount
        cat.x += dx
        cat.y += dy
        let moved = hypot(dx, dy)
        phase += moved / (catSize * 0.22)
        if abs(dx) > 0.6 { facingRight = dx > 0 }
        if moved > 0.5 {
            let target = max(-28, min(28, atan2(dy, abs(dx) + 0.001) * 180 / .pi))
            tilt += (target - tilt) * 0.3
        } else {
            tilt *= 0.85
        }
    }

    /// Draws the trail and cat. Returns true while another frame is needed.
    func draw(in ctx: CGContext, color: UIColor, eye: UIColor) -> Bool {
        guard points.count >= 2 else { return false }
        var fade: CGFloat = 1
        if fadeStart > 0 {
            fade = 1 - CGFloat((CACurrentMediaTime() - fadeStart) / fadeDuration)
            if fade <= 0 { clear(); return false }
        } else {
            chase(0.25)
        }
        drawCurve(ctx, color, fade)
        if showCat { drawCat(ctx, color, eye, fade) }
        return true
    }

    private func drawCurve(_ ctx: CGContext, _ color: UIColor, _ fade: CGFloat) {
        let n = points.count
        let from = max(0, n - 90)
        let chunks = 10
        let per = max(2, (n - from) / chunks)
        var start = from, i = 0
        ctx.setLineJoin(.round)
        while start < n - 1 {
            let end = min(n - 1, start + per)
            let t = min(1, CGFloat(i + 1) / CGFloat(chunks))
            let path = CGMutablePath()
            path.move(to: points[start])
            if end > start + 1 {
                for k in (start + 1)..<end {
                    let mid = CGPoint(x: (points[k].x + points[k + 1].x) / 2, y: (points[k].y + points[k + 1].y) / 2)
                    path.addQuadCurve(to: mid, control: points[k])
                }
            }
            path.addLine(to: points[end])
            ctx.addPath(path)
            ctx.setLineWidth(1.5 + 4.5 * t)
            ctx.setLineCap(end == n - 1 ? .round : .butt)
            ctx.setStrokeColor(color.withAlphaComponent((0.16 + 0.66 * t) * fade).cgColor)
            ctx.strokePath()
            start = end
            i += 1
        }
    }

    private func drawCat(_ ctx: CGContext, _ color: UIColor, _ eye: UIColor, _ fade: CGFloat) {
        let s = catSize
        let fill = color.withAlphaComponent(fade).cgColor
        ctx.saveGState()
        ctx.translateBy(x: cat.x, y: cat.y + sin(phase * 2) * 0.03 * s)
        if !facingRight { ctx.scaleBy(x: -1, y: 1) }
        ctx.rotate(by: tilt * .pi / 180)

        // Legs: a gallop, front and back pairs half a cycle apart.
        ctx.setStrokeColor(fill)
        ctx.setLineCap(.round)
        ctx.setLineWidth(0.085 * s)
        let legLen = 0.30 * s
        for (hip, offset) in [(0.22, 0.0), (0.30, 0.6), (-0.30, Double.pi), (-0.22, Double.pi + 0.6)] {
            let a = sin(phase + CGFloat(offset)) * 0.75
            let hx = CGFloat(hip) * s, hy = 0.08 * s
            ctx.move(to: CGPoint(x: hx, y: hy))
            ctx.addLine(to: CGPoint(x: hx + sin(a) * legLen, y: hy + cos(a) * legLen))
        }
        ctx.strokePath()

        // Body, stretching a little on each stride.
        let stretch = 1 + sin(phase) * 0.05
        ctx.setFillColor(fill)
        ctx.addPath(CGPath(roundedRect: CGRect(x: -0.44 * s * stretch, y: -0.17 * s, width: 0.80 * s * stretch, height: 0.32 * s),
                           cornerWidth: 0.16 * s, cornerHeight: 0.16 * s, transform: nil))
        ctx.fillPath()

        // Tail.
        let sway = sin(phase * 0.5) * 0.12 * s
        ctx.setLineWidth(0.08 * s)
        ctx.move(to: CGPoint(x: -0.40 * s, y: -0.08 * s))
        ctx.addCurve(to: CGPoint(x: -0.62 * s, y: -0.52 * s - sway),
                     control1: CGPoint(x: -0.70 * s, y: -0.10 * s + sway), control2: CGPoint(x: -0.78 * s, y: -0.40 * s))
        ctx.strokePath()

        // Head and ears.
        ctx.fillEllipse(in: CGRect(x: 0.27 * s, y: -0.39 * s, width: 0.38 * s, height: 0.38 * s))
        ctx.move(to: CGPoint(x: 0.32 * s, y: -0.30 * s)); ctx.addLine(to: CGPoint(x: 0.35 * s, y: -0.52 * s)); ctx.addLine(to: CGPoint(x: 0.47 * s, y: -0.36 * s))
        ctx.closePath()
        ctx.move(to: CGPoint(x: 0.48 * s, y: -0.37 * s)); ctx.addLine(to: CGPoint(x: 0.61 * s, y: -0.52 * s)); ctx.addLine(to: CGPoint(x: 0.62 * s, y: -0.30 * s))
        ctx.closePath()
        ctx.fillPath()

        // Eye.
        ctx.setFillColor(eye.withAlphaComponent(fade).cgColor)
        ctx.fillEllipse(in: CGRect(x: 0.53 * s - 0.032 * s, y: -0.22 * s - 0.032 * s, width: 0.064 * s, height: 0.064 * s))
        ctx.restoreGState()
    }
}
