package com.syed.slate.core

import java.text.Normalizer

/**
 * Question identity, bit-for-bit the same as the web apps' `qid.js`.
 *
 * Saved flags and highlights are keyed by this uid, so a single differing bit
 * detaches every bit of progress from its question. UidTest checks it against
 * uids stored in both live databases.
 */
object Uid {
    private val zeroWidth = Regex("[\\u200B-\\u200D\\uFEFF]")
    private val whitespace = Regex("\\s+")

    /** NFC, strip zero-width/BOM, lowercase, collapse whitespace, trim. */
    fun normalize(s: String?): String =
        Normalizer.normalize(s.orEmpty(), Normalizer.Form.NFC)
            .replace(zeroWidth, "")
            .lowercase()
            .replace(whitespace, " ")
            .trim()

    /** cyrb53 over UTF-16 code units, exactly as JS `charCodeAt` sees them. */
    fun cyrb53(str: String, seed: Int = 0): Long {
        var h1 = 0xdeadbeef.toInt() xor seed
        var h2 = 0x41c6ce57 xor seed
        for (ch in str) {
            h1 = (h1 xor ch.code) * 2654435761L.toInt()
            h2 = (h2 xor ch.code) * 1597334677
        }
        h1 = (h1 xor (h1 ushr 16)) * 2246822507L.toInt()
        h1 = h1 xor ((h2 xor (h2 ushr 13)) * 3266489909L.toInt())
        h2 = (h2 xor (h2 ushr 16)) * 2246822507L.toInt()
        h2 = h2 xor ((h1 xor (h1 ushr 13)) * 3266489909L.toInt())
        return 4294967296L * (2097151 and h2).toLong() + (h1.toLong() and 0xFFFFFFFFL)
    }

    /** general-quiz style: `q<base36>`. Null for empty text. */
    fun general(text: String?): String? {
        val n = normalize(text)
        return if (n.isEmpty()) null else "q" + cyrb53(n).toString(36)
    }

    /** ict-quiz style: `<module>:<base36>` (mcq, written, extra, viva, equation...). */
    fun ict(module: String, text: String?): String? {
        val n = normalize(text)
        return if (n.isEmpty()) null else "$module:" + cyrb53(n).toString(36)
    }
}
