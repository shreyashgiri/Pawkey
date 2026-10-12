# Installing PawKeys on an iPhone

PawKeys for iPhone comes as **PawKeys.ipa**, the iPhone version of an .apk.

**Download:** the latest build is always at
**https://github.com/shreyashgiri/Pawkey/releases/tag/ios-latest**
GitHub rebuilds it automatically on every change to the `ios/` folder.

## Why an .ipa doesn't install with one tap

Apple only runs apps that are **signed** with an Apple ID. An .apk installs directly on Android; an .ipa first has to be signed and installed by a tool on a computer. You don't need a Mac: **Sideloadly** works on Windows.

| You use | App lasts | Cost |
|---|---|---|
| A free Apple ID | 7 days, then re-install it with Sideloadly (it keeps your settings) | Free |
| A paid Apple Developer account | 1 year | $99/year |

## Install with Sideloadly (Windows or Mac)

1. Install **iTunes** from apple.com (not the Microsoft Store version) and **iCloud**. Sideloadly needs their drivers to talk to the iPhone.
2. Download **Sideloadly** from sideloadly.io and install it.
3. Download **PawKeys.ipa** from the link above.
4. Plug your iPhone into the PC with a cable. Unlock it and tap **Trust** if it asks.
5. Open Sideloadly. Drag **PawKeys.ipa** onto it, type your **Apple ID** email, and press **Start**. Enter your Apple ID password when asked. It goes only to Apple.
6. On the iPhone, open **Settings → General → VPN & Device Management**. Tap your Apple ID and then **Trust**.
7. **iOS 16 and later:** turn on **Settings → Privacy & Security → Developer Mode** and restart the iPhone when asked.

Free Apple IDs can have 3 sideloaded apps at a time. After 7 days PawKeys stops opening; plug in and press **Start** in Sideloadly again to refresh it.

**AltStore** (altstore.io) is an alternative to Sideloadly that can also refresh apps over Wi-Fi.

## Turn the keyboard on

1. Open **PawKeys** and tap **Open Settings**. It opens PawKeys in the Settings app.
2. Tap **Keyboards** and turn on **PawKeys**.
3. Turn on **Allow Full Access**. This is needed for key sounds, haptics, Stash (clipboard), stickers, GIFs and paste. PawKeys never sends what you type anywhere.
4. In any text box, hold the 🌐 globe key and choose **PawKeys**.

All settings are inside the keyboard: tap the paw 🐾, then **Settings**.

## What's different on iPhone

Apple limits what keyboards can do, so a few features work differently from Android:

| Feature | On iPhone |
|---|---|
| Stickers and GIFs | Copied for you to paste; keyboards can't insert images |
| Stash (clipboard) | Picks up new copies when you open it (needs Full Access) |
| Groom | Cursor, word and line jumps, copy, cut and paste. No select mode; keyboards can't select text |
| Scratch (swipe-left delete) | Shows how many words will go, then deletes them when you let go |
| Translate | Apple's on-device translation, iOS 18+. Get languages in the Translate app first |
| Voice typing | Not possible; Apple blocks the microphone in keyboards. Use the iPhone's own mic key |
| Perch (floating keyboard) | Not possible; iPhone keyboards are fixed to the bottom |
| Key sounds | The iPhone click works always; Kitten, Cat, Duck and Click need Full Access |
