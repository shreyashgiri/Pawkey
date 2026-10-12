package com.pawkeys.keyboard.engine

/** Words never offered as suggestions (they still work if typed in full). */
object Offensive {
    private val words = setOf(
        "fuck", "fucking", "fucked", "fucker", "fucks", "motherfucker", "shit", "shitty", "bullshit",
        "bitch", "bitches", "cunt", "dick", "dickhead", "cock", "pussy", "asshole", "bastard",
        "whore", "slut", "nigger", "nigga", "faggot", "fag", "retard", "retarded", "twat", "wanker",
    )

    fun contains(word: String) = word.lowercase() in words
}
