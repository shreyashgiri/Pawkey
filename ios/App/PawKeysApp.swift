import SwiftUI

@main
struct PawKeysApp: App {
    var body: some Scene {
        WindowGroup { SetupView() }
    }
}

/// The app itself is a setup guide; the keyboard lives in the PawKeys keyboard extension.
struct SetupView: View {
    @State private var test = ""
    private let accent = Color(red: 0.851, green: 0.467, blue: 0.227)

    var body: some View {
        NavigationStack {
            List {
                Section {
                    TextField("Try PawKeys here", text: $test, axis: .vertical)
                        .lineLimit(2...5)
                } footer: {
                    Text("Tap the globe key 🌐 on your keyboard to switch to PawKeys.")
                }

                Section("Get started") {
                    step(1, "Open Settings", "Tap the button below. It opens PawKeys in the Settings app.")
                    step(2, "Keyboards → turn on PawKeys", "Then turn on Allow Full Access for sounds, Stash, stickers, GIFs and paste.")
                    step(3, "Switch to PawKeys", "In any text box, hold the globe key 🌐 and pick PawKeys.")
                    Button {
                        if let url = URL(string: UIApplication.openSettingsURLString) { UIApplication.shared.open(url) }
                    } label: {
                        Label("Open Settings", systemImage: "gearshape")
                            .frame(maxWidth: .infinity)
                    }
                    .buttonStyle(.borderedProminent)
                    .tint(accent)
                }

                Section("Why Full Access?") {
                    Text("Without it, iPhone keyboards can't play sounds, read what you copied (Stash), put stickers or GIFs on the clipboard, or load GIFs. PawKeys never sends what you type anywhere.")
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                }

                Section("Settings") {
                    Text("All PawKeys settings are inside the keyboard: tap the paw 🐾, then Settings.")
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                }
            }
            .navigationTitle("PawKeys")
        }
        .tint(accent)
    }

    private func step(_ n: Int, _ title: String, _ detail: String) -> some View {
        HStack(alignment: .top, spacing: 12) {
            Text("\(n)")
                .font(.footnote.weight(.semibold))
                .foregroundStyle(.white)
                .frame(width: 24, height: 24)
                .background(Circle().fill(accent))
            VStack(alignment: .leading, spacing: 2) {
                Text(title).font(.body.weight(.medium))
                Text(detail).font(.footnote).foregroundStyle(.secondary)
            }
        }
        .padding(.vertical, 2)
    }
}
