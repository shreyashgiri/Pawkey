package com.pawkey.keyboard.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import java.io.File

class EngineTest {
    companion object {
        lateinit var dict: Dictionary
        lateinit var glide: GlideDecoder

        @BeforeClass @JvmStatic
        fun load() {
            dict = File("src/main/assets/words_en.txt").inputStream().use { Dictionary(it) }
            glide = GlideDecoder(dict)
        }

        /** Key centres of a 10-column QWERTY keyboard with 100px keys. */
        val centers: Map<Char, Pair<Float, Float>> = HashMap<Char, Pair<Float, Float>>().apply {
            "qwertyuiop".forEachIndexed { i, c -> put(c, 50f + i * 100f to 50f) }
            "asdfghjkl".forEachIndexed { i, c -> put(c, 100f + i * 100f to 150f) }
            "zxcvbnm".forEachIndexed { i, c -> put(c, 200f + i * 100f to 250f) }
        }

        /** Builds a finger trace through [word]'s keys with a little wobble. */
        fun trace(word: String): Pair<FloatArray, FloatArray> {
            val xs = ArrayList<Float>(); val ys = ArrayList<Float>()
            val pts = word.map { centers[it]!! }
            for (i in 0 until pts.size - 1) {
                for (s in 0 until 8) {
                    val t = s / 8f
                    xs += pts[i].first + (pts[i + 1].first - pts[i].first) * t + (s % 3 - 1) * 6f
                    ys += pts[i].second + (pts[i + 1].second - pts[i].second) * t + (s % 2) * 8f
                }
            }
            xs += pts.last().first; ys += pts.last().second
            return xs.toFloatArray() to ys.toFloatArray()
        }
    }

    @Test fun completesPrefixes() {
        val s = dict.suggest("hel").map { it.word }
        assertTrue(s.toString(), "hello" in s || "help" in s)
    }

    @Test fun correctsCommonTypos() {
        assertEquals("the", dict.autocorrect("teh"))
        assertEquals("don't", dict.autocorrect("dont"))
        assertEquals("I'm", dict.autocorrect("Im"))
        assertEquals("I", dict.autocorrect("i"))
        assertEquals("keyboard", dict.autocorrect("keyboarf"))
        assertEquals("Hello", dict.autocorrect("Hrllo"))
    }

    @Test fun leavesRealWordsAlone() {
        assertNull(dict.autocorrect("cat"))
        assertNull(dict.autocorrect("its"))
        assertNull(dict.autocorrect("were"))
        assertNull(dict.autocorrect("NASA"))
        assertNull(dict.autocorrect("abc123"))
    }

    @Test fun learnedWordsSuppressCorrection() {
        assertNull(dict.autocorrect("pawkey", extra = mapOf("pawkey" to 4000)))
    }

    @Test fun matchesCase() {
        assertEquals("HELLO", Dictionary.matchCase("HE", "hello"))
        assertEquals("Hello", Dictionary.matchCase("He", "hello"))
    }

    @Test fun decodesGlideTraces() {
        for (word in listOf("hello", "cat", "keyboard", "with", "about", "people")) {
            val (xs, ys) = trace(word)
            val result = glide.decode(xs, ys, centers, 100f)
            assertTrue("$word -> $result", word in result.take(2))
        }
    }

    @Test fun userDictionaryLearnsAndPredicts() {
        val dir = createTempDir()
        val u = UserDictionary(dir)
        u.learnBigram("good", "morning"); u.learnBigram("good", "morning"); u.learnBigram("good", "night")
        u.learnWord("pawkey"); u.learnWord("pawkey")
        u.save()
        val reloaded = UserDictionary(dir)
        assertEquals(listOf("morning", "night"), reloaded.predictNext("Good"))
        assertTrue("pawkey" in reloaded.learned)
        reloaded.block("night")
        assertEquals(listOf("morning"), reloaded.predictNext("good"))
        dir.deleteRecursively()
    }
}
