package com.pawkeys.keyboard.panels

import com.pawkeys.keyboard.R

class EmojiCategory(val name: String, val icon: Int, val emojis: List<String>)

/** A curated emoji set. Cats get their own tab, of course. */
object EmojiData {
    private fun list(s: String) = s.trim().split(Regex("\\s+"))

    val categories = listOf(
        EmojiCategory("Cats", R.drawable.ic_pets, list("""
            😺 😸 😹 😻 😼 😽 🙀 😿 😾 🐱 🐈 🐈‍⬛ 🐾 🦁 🐯 🐅 🐆 🧶 🐟 🐠 🥛 🐭 🐁 📦 🛋️ 🌙 💤 ❤️‍🔥 🫶 😻
        """).distinct()),
        EmojiCategory("Smileys", R.drawable.ic_mood, list("""
            😀 😃 😄 😁 😆 😅 🤣 😂 🙂 🙃 🫠 😉 😊 😇 🥰 😍 🤩 😘 😗 ☺️ 😚 😙 🥲 😋 😛 😜 🤪 😝 🤑 🤗 🤭 🫢 🫣 🤫 🤔 🫡
            🤐 🤨 😐 😑 😶 🫥 😏 😒 🙄 😬 🤥 😌 😔 😪 🤤 😴 😷 🤒 🤕 🤢 🤮 🤧 🥵 🥶 🥴 😵 🤯 🤠 🥳 🥸 😎 🤓 🧐 😕 🫤 😟
            🙁 ☹️ 😮 😯 😲 😳 🥺 🥹 😦 😧 😨 😰 😥 😢 😭 😱 😖 😣 😞 😓 😩 😫 🥱 😤 😡 😠 🤬 😈 👿 💀 ☠️ 💩 🤡 👻 👽 🤖
            👋 🤚 🖐️ ✋ 🖖 👌 🤌 🤏 ✌️ 🤞 🫰 🤟 🤘 🤙 👈 👉 👆 👇 ☝️ 🫵 👍 👎 ✊ 👊 🤛 🤜 👏 🙌 🫶 👐 🤲 🤝 🙏 ✍️ 💪
            ❤️ 🧡 💛 💚 💙 💜 🖤 🤍 🤎 💔 ❣️ 💕 💞 💓 💗 💖 💘 💝 💯 💢 💥 💫 💦 💨 🕳️ 💬 💭 💤
        """)),
        EmojiCategory("Animals & nature", R.drawable.ic_emoji_nature, list("""
            🐶 🐕 🦮 🐩 🐺 🦊 🦝 🐴 🦄 🦓 🦌 🐮 🐂 🐷 🐖 🐗 🐏 🐑 🐐 🐪 🦙 🦒 🐘 🦣 🦏 🐭 🐹 🐰 🐇 🐿️ 🦫 🦔 🦇 🐻 🐻‍❄️ 🐨
            🐼 🦥 🦦 🦨 🦘 🦡 🦃 🐔 🐓 🐣 🐤 🐥 🐦 🐧 🕊️ 🦅 🦆 🦢 🦉 🦩 🦚 🦜 🐸 🐊 🐢 🦎 🐍 🐲 🐉 🦕 🦖 🐳 🐋 🐬 🦭 🐡 🦈
            🐙 🐚 🐌 🦋 🐛 🐜 🐝 🪲 🐞 🦗 🕷️ 🦂 💐 🌸 💮 🌹 🥀 🌺 🌻 🌼 🌷 🌱 🪴 🌲 🌳 🌴 🌵 🌾 🌿 ☘️ 🍀 🍁 🍂 🍃 🍄 🌍 🌙 ⭐ 🌟
            ☀️ 🌤️ ⛅ 🌧️ ⛈️ 🌩️ ❄️ ☃️ ⛄ 🌈 🔥 💧 🌊
        """)),
        EmojiCategory("Food & drink", R.drawable.ic_emoji_food_beverage, list("""
            🍏 🍎 🍐 🍊 🍋 🍌 🍉 🍇 🍓 🫐 🍈 🍒 🍑 🥭 🍍 🥥 🥝 🍅 🍆 🥑 🥦 🥬 🥒 🌶️ 🫑 🌽 🥕 🧄 🧅 🥔 🍠 🥐 🥯 🍞 🥖 🥨
            🧀 🥚 🍳 🧈 🥞 🧇 🥓 🥩 🍗 🍖 🌭 🍔 🍟 🍕 🫓 🥪 🥙 🧆 🌮 🌯 🫔 🥗 🥘 🫕 🍝 🍜 🍲 🍛 🍣 🍱 🥟 🦪 🍤 🍙 🍚 🍘
            🍥 🥠 🥮 🍢 🍡 🍧 🍨 🍦 🥧 🧁 🍰 🎂 🍮 🍭 🍬 🍫 🍿 🍩 🍪 🌰 🥜 🍯 🥛 🍼 🫖 ☕ 🍵 🧃 🥤 🧋 🍶 🍺 🍻 🥂 🍷 🥃 🍸 🍹
        """)),
        EmojiCategory("Activities", R.drawable.ic_emoji_events, list("""
            ⚽ 🏀 🏈 ⚾ 🥎 🎾 🏐 🏉 🥏 🎱 🪀 🏓 🏸 🏒 🏑 🥍 🏏 🪃 🥅 ⛳ 🪁 🏹 🎣 🤿 🥊 🥋 🎽 🛹 🛼 🛷 ⛸️ 🥌 🎿 ⛷️ 🏂 🏋️
            🤸 ⛹️ 🤺 🤾 🏌️ 🏇 🧘 🏄 🏊 🤽 🚣 🧗 🚵 🚴 🏆 🥇 🥈 🥉 🏅 🎖️ 🎗️ 🎫 🎟️ 🎪 🤹 🎭 🩰 🎨 🎬 🎤 🎧 🎼 🎹 🥁 🎷 🎺
            🪗 🎸 🪕 🎻 🎲 ♟️ 🎯 🎳 🎮 🎰 🧩 🎉 🎊 🎈 🎁 🎀 🎄 🎃 ✨
        """)),
        EmojiCategory("Travel & places", R.drawable.ic_emoji_transportation, list("""
            🚗 🚕 🚙 🚌 🚎 🏎️ 🚓 🚑 🚒 🚐 🛻 🚚 🚛 🚜 🛵 🏍️ 🛺 🚲 🛴 🚨 🚔 🚍 🚘 🚖 🚡 🚠 🚟 🚃 🚋 🚞 🚝 🚄 🚅 🚈 🚂 🚆
            🚇 🚊 🚉 ✈️ 🛫 🛬 🛩️ 💺 🛰️ 🚀 🛸 🚁 🛶 ⛵ 🚤 🛥️ 🛳️ ⛴️ 🚢 ⚓ ⛽ 🚧 🚦 🚥 🗺️ 🗿 🗽 🗼 🏰 🏯 🏟️ 🎡 🎢 🎠 ⛲ ⛱️
            🏖️ 🏝️ 🏜️ 🌋 ⛰️ 🏔️ 🗻 🏕️ ⛺ 🏠 🏡 🏘️ 🏗️ 🏭 🏢 🏬 🏣 🏤 🏥 🏦 🏨 🏪 🏫 🏩 💒 🏛️ ⛪ 🕌 🕍 🛕 🌅 🌄 🌠 🎇 🎆 🌇
            🌆 🏙️ 🌃 🌌 🌉 🌁
        """)),
        EmojiCategory("Objects", R.drawable.ic_emoji_objects, list("""
            ⌚ 📱 📲 💻 ⌨️ 🖥️ 🖨️ 🖱️ 🕹️ 💽 💾 💿 📀 📷 📸 📹 🎥 📞 ☎️ 📺 📻 🎙️ ⏰ ⌛ ⏳ 📡 🔋 🔌 💡 🔦 🕯️ 🧯 💸 💵 💳 💎
            ⚖️ 🧰 🔧 🔨 🛠️ ⛏️ 🔩 ⚙️ 🧲 🔫 💣 🔪 🗡️ 🛡️ 🔮 🧿 💈 🔭 🔬 💊 💉 🩹 🧬 🌡️ 🧹 🧺 🧻 🚽 🛁 🧼 🪥 🛎️ 🔑 🗝️ 🚪 🪑
            🛋️ 🛏️ 🧸 🖼️ 🛍️ 🛒 🎁 🎈 ✉️ 📩 📨 📧 💌 📦 🏷️ 📪 📬 📮 📜 📃 📄 📑 📊 📈 📉 🗒️ 🗓️ 📆 📅 🗑️ 📇 🗃️ 📋 📁 📂 🗂️
            📰 📓 📔 📒 📕 📗 📘 📙 📚 📖 🔖 🔗 📎 🖇️ 📐 📏 📌 📍 ✂️ 🖊️ 🖋️ ✒️ 🖌️ 🖍️ 📝 ✏️ 🔍 🔎 🔏 🔐 🔒 🔓
        """)),
        EmojiCategory("Symbols", R.drawable.ic_emoji_symbols, list("""
            ☮️ ✝️ ☪️ 🕉️ ☸️ ✡️ 🔯 🕎 ☯️ ☦️ 🛐 ⛎ ♈ ♉ ♊ ♋ ♌ ♍ ♎ ♏ ♐ ♑ ♒ ♓ 🆔 ⚛️ ☢️ ☣️ 📴 📳 🈶 🈚 🆚 💮 🉐 ㊙️ ㊗️ 🅰️ 🅱️
            🆎 🆑 🅾️ 🆘 ❌ ⭕ 🛑 ⛔ 📛 🚫 💯 💢 ♨️ 🚷 🚯 🚳 🚱 🔞 📵 🚭 ❗ ❕ ❓ ❔ ‼️ ⁉️ 🔅 🔆 〽️ ⚠️ 🚸 🔱 ⚜️ 🔰 ♻️ ✅ 🈯 💹
            ❇️ ✳️ ❎ 🌐 💠 Ⓜ️ 🌀 💤 🏧 🚾 ♿ 🅿️ 🛗 🚹 🚺 🚼 ⚧️ 🚻 🚮 🎦 📶 🈁 🔣 ℹ️ 🔤 🔡 🔠 🆖 🆗 🆙 🆒 🆕 🆓 0️⃣ 1️⃣ 2️⃣
            3️⃣ 4️⃣ 5️⃣ 6️⃣ 7️⃣ 8️⃣ 9️⃣ 🔟 🔢 #️⃣ *️⃣ ▶️ ⏸️ ⏯️ ⏹️ ⏺️ ⏭️ ⏮️ ⏩ ⏪ 🔀 🔁 🔂 ◀️ 🔼 🔽 ➡️ ⬅️ ⬆️ ⬇️ ↗️ ↘️ ↙️ ↖️ ↕️
            ↔️ 🔄 ➕ ➖ ➗ ✖️ 🟰 ♾️ 💲 💱 ™️ ©️ ®️ 〰️ ➰ ➿ 🔚 🔙 🔛 🔝 🔜 ✔️ ☑️ 🔘 🔴 🟠 🟡 🟢 🔵 🟣 ⚫ ⚪ 🟤 🔺 🔻 🔸 🔹 🔶 🔷
            🔳 🔲 ▪️ ▫️ ◾ ◽ ◼️ ◻️ 🟥 🟧 🟨 🟩 🟦 🟪 ⬛ ⬜ 🟫 🔈 🔇 🔉 🔊 🔔 🔕 📣 📢 ♠️ ♣️ ♥️ ♦️ 🃏 🎴 🀄
        """)),
        EmojiCategory("Flags", R.drawable.ic_emoji_flags, list("""
            🏁 🚩 🎌 🏴 🏳️ 🏳️‍🌈 🏳️‍⚧️ 🏴‍☠️ 🇺🇳 🇦🇷 🇦🇺 🇦🇹 🇧🇩 🇧🇪 🇧🇷 🇨🇦 🇨🇱 🇨🇳 🇨🇴 🇩🇰 🇪🇬 🇫🇮 🇫🇷 🇩🇪 🇬🇷 🇭🇰 🇮🇸 🇮🇳 🇮🇩 🇮🇷 🇮🇪
            🇮🇱 🇮🇹 🇯🇵 🇰🇪 🇲🇾 🇲🇽 🇳🇵 🇳🇱 🇳🇿 🇳🇬 🇳🇴 🇵🇰 🇵🇪 🇵🇭 🇵🇱 🇵🇹 🇶🇦 🇷🇴 🇷🇺 🇸🇦 🇸🇬 🇿🇦 🇰🇷 🇪🇸 🇱🇰 🇸🇪 🇨🇭 🇹🇼 🇹🇭 🇹🇷 🇺🇦 🇦🇪
            🇬🇧 🇺🇸 🇻🇳
        """)),
    )
}
