import UIKit

/// Small helpers so panels look like the keys.
enum PanelKit {
    static func button(_ title: String?, symbol: String? = nil, coat: Coat, background: UIColor? = nil, tint: UIColor? = nil,
                       size: CGFloat = 15, weight: UIFont.Weight = .medium, action: @escaping () -> Void) -> UIButton {
        var cfg = UIButton.Configuration.filled()
        cfg.baseBackgroundColor = background ?? coat.functionKey
        cfg.baseForegroundColor = tint ?? coat.keyText
        cfg.background.cornerRadius = 12
        cfg.cornerStyle = .fixed
        if let s = symbol { cfg.image = UIImage(systemName: s, withConfiguration: UIImage.SymbolConfiguration(pointSize: 16, weight: .regular)) }
        if let t = title {
            var a = AttributedString(t)
            a.font = UIFont.systemFont(ofSize: size, weight: weight)
            cfg.attributedTitle = a
        }
        cfg.contentInsets = NSDirectionalEdgeInsets(top: 4, leading: 6, bottom: 4, trailing: 6)
        let b = UIButton(configuration: cfg, primaryAction: UIAction { _ in action() })
        b.titleLabel?.adjustsFontSizeToFitWidth = true
        b.accessibilityLabel = title ?? symbol
        return b
    }

    static func label(_ text: String, coat: Coat, size: CGFloat = 15, weight: UIFont.Weight = .medium, color: UIColor? = nil) -> UILabel {
        let l = UILabel()
        l.text = text
        l.font = UIFont.systemFont(ofSize: size, weight: weight)
        l.textColor = color ?? coat.keyText
        return l
    }

    /// Panel header: a quiet title and an optional text action on the right.
    static func header(_ title: String, coat: Coat, action: String? = nil, onAction: @escaping () -> Void = {}) -> UIView {
        let row = UIStackView()
        row.axis = .horizontal
        row.alignment = .center
        row.isLayoutMarginsRelativeArrangement = true
        row.directionalLayoutMargins = NSDirectionalEdgeInsets(top: 0, leading: 16, bottom: 0, trailing: 8)
        row.addArrangedSubview(label(title, coat: coat))
        row.addArrangedSubview(UIView())
        if let a = action {
            let b = UIButton(type: .system)
            b.setTitle(a, for: .normal)
            b.titleLabel?.font = UIFont.systemFont(ofSize: 14, weight: .medium)
            b.tintColor = coat.accent
            b.addAction(UIAction { _ in onAction() }, for: .touchUpInside)
            row.addArrangedSubview(b)
        }
        row.heightAnchor.constraint(equalToConstant: 38).isActive = true
        return row
    }

    /// Vertical stack of horizontal rows, filling the panel.
    static func grid(_ views: [UIView], columns: Int, spacing: CGFloat = 6) -> UIStackView {
        let v = UIStackView()
        v.axis = .vertical
        v.spacing = spacing
        v.distribution = .fillEqually
        stride(from: 0, to: views.count, by: columns).forEach { i in
            let row = UIStackView(arrangedSubviews: Array(views[i..<min(i + columns, views.count)]))
            row.axis = .horizontal
            row.spacing = spacing
            row.distribution = .fillEqually
            while row.arrangedSubviews.count < columns { row.addArrangedSubview(UIView()) }
            v.addArrangedSubview(row)
        }
        return v
    }

    static func pin(_ child: UIView, in parent: UIView, insets: UIEdgeInsets = .zero) {
        child.translatesAutoresizingMaskIntoConstraints = false
        parent.addSubview(child)
        NSLayoutConstraint.activate([
            child.topAnchor.constraint(equalTo: parent.topAnchor, constant: insets.top),
            child.bottomAnchor.constraint(equalTo: parent.bottomAnchor, constant: -insets.bottom),
            child.leadingAnchor.constraint(equalTo: parent.leadingAnchor, constant: insets.left),
            child.trailingAnchor.constraint(equalTo: parent.trailingAnchor, constant: -insets.right),
        ])
    }
}

/// Single paw side rail: full width, or move to the other side.
final class SinglePawRail: UIView {
    init(coat: Coat, side: String, action: @escaping (String) -> Void) {
        super.init(frame: .zero)
        backgroundColor = coat.background
        let stack = UIStackView(arrangedSubviews: [
            PanelKit.button(nil, symbol: "arrow.up.left.and.arrow.down.right", coat: coat) { action("full") },
            PanelKit.button(nil, symbol: side == "left" ? "chevron.right" : "chevron.left", coat: coat) { action("move") },
        ])
        stack.axis = .vertical
        stack.spacing = 14
        stack.translatesAutoresizingMaskIntoConstraints = false
        addSubview(stack)
        NSLayoutConstraint.activate([
            stack.centerXAnchor.constraint(equalTo: centerXAnchor), stack.centerYAnchor.constraint(equalTo: centerYAnchor),
            stack.widthAnchor.constraint(equalToConstant: 44),
            stack.arrangedSubviews[0].heightAnchor.constraint(equalToConstant: 44),
            stack.arrangedSubviews[1].heightAnchor.constraint(equalToConstant: 44),
        ])
    }

    required init?(coder: NSCoder) { fatalError() }
}
