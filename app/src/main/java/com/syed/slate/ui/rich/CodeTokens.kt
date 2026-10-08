package com.syed.slate.ui.rich

enum class TokenKind { KEYWORD, TYPE, STRING, NUMBER, COMMENT, FUNCTION, META }

class Token(val start: Int, val end: Int, val kind: TokenKind)

/**
 * A small syntax tokenizer for the languages the cards use (c, cpp, java, sql). It does not parse; it only needs
 * to colour keywords, strings, numbers, comments and calls well enough to read. An unknown language yields no
 * tokens, so the code simply shows as plain text.
 */
object CodeTokens {
    private val cKeywords = "auto break case const continue default do else enum extern for goto if inline register return sizeof static struct switch typedef union volatile while".split(' ').toSet()
    private val cTypes = "int char float double void long short signed unsigned size_t FILE bool".split(' ').toSet()
    private val cppExtra = "class namespace new delete template typename public private protected virtual using try catch throw this nullptr true false operator friend explicit override final".split(' ').toSet()
    private val cppTypes = cTypes + "string vector map set list pair".split(' ')
    private val javaKeywords = "abstract assert break case catch class continue default do else enum extends final finally for if implements import instanceof interface native new package private protected public return static super switch synchronized this throw throws transient try volatile while true false null".split(' ').toSet()
    private val javaTypes = "boolean byte char double float int long short void var".split(' ').toSet()
    private val sqlKeywords = ("select from where group by having order insert into values update set delete create alter drop table index view " +
        "join inner left right full outer cross on as and or not in is null like between exists distinct union all limit offset " +
        "primary key foreign references default check unique constraint case when then else end asc desc count sum avg min max begin commit rollback grant revoke").split(' ').toSet()
    private val sqlTypes = "int integer smallint bigint decimal numeric float double real char varchar text date time timestamp boolean".split(' ').toSet()

    private fun regexFor(lang: String): Regex {
        val comment = if (lang == "sql") """--[^\n]*|/\*[\s\S]*?\*/""" else """//[^\n]*|/\*[\s\S]*?\*/"""
        val string = if (lang == "sql") """'(?:''|[^'\n])*'""" else """"(?:\\.|[^"\\\n])*"|'(?:\\.|[^'\\\n])*'"""
        val meta = if (lang == "c" || lang == "cpp") """^[ \t]*#[^\n]*""" else """(?!)"""
        return Regex("($comment)|($string)|($meta)|(\\b0[xX][0-9a-fA-F]+\\b|\\b\\d+(?:\\.\\d+)?[fFlLuU]*\\b)|([A-Za-z_][A-Za-z0-9_]*)", RegexOption.MULTILINE)
    }

    private val regexes = HashMap<String, Regex>()

    /** A best guess for a listing that names no language (MCQ code): C unless it clearly reads as SQL, Java or C++. Mirrors ict-quiz's guessLang. */
    fun guessLang(code: String): String = when {
        !code.contains('{') && !code.contains('}') &&
            Regex("""\b(select\s[\s\S]*\sfrom|insert\s+into|update\s+\w+\s+set|create\s+table|delete\s+from)\b""", RegexOption.IGNORE_CASE).containsMatchIn(code) -> "sql"
        Regex("""\bSystem\.out\b|\bpublic\s+(static\s+)?(class|void)\b|\bString\[]""").containsMatchIn(code) -> "java"
        Regex("""\bcout\b|\bcin\b|#include\s*<iostream>|\bstd::|\btemplate\s*<""").containsMatchIn(code) -> "cpp"
        else -> "c"
    }

    fun normalizeLang(lang: String?): String? = when ((lang ?: "c").lowercase()) {
        "c" -> "c"; "cpp", "c++" -> "cpp"; "java" -> "java"; "sql" -> "sql"; else -> null
    }

    fun tokenize(code: String, lang: String?): List<Token> {
        val l = normalizeLang(lang) ?: return emptyList()
        val keywords = when (l) { "c" -> cKeywords; "cpp" -> cKeywords + cppExtra; "java" -> javaKeywords; else -> sqlKeywords }
        val types = when (l) { "c" -> cTypes; "cpp" -> cppTypes; "java" -> javaTypes; else -> sqlTypes }
        val out = ArrayList<Token>()
        for (m in regexes.getOrPut(l) { regexFor(l) }.findAll(code)) {
            val r = m.range; val s = r.first; val e = r.last + 1
            val kind = when {
                m.groups[1] != null -> TokenKind.COMMENT
                m.groups[2] != null -> TokenKind.STRING
                m.groups[3] != null -> TokenKind.META
                m.groups[4] != null -> TokenKind.NUMBER
                else -> {
                    val w = m.value; val key = if (l == "sql") w.lowercase() else w
                    when {
                        key in types -> TokenKind.TYPE
                        key in keywords -> TokenKind.KEYWORD
                        // Java class names read as types; a name followed by "(" is a call.
                        l == "java" && w[0].isUpperCase() && !code.startsWith("(", e) -> TokenKind.TYPE
                        code.startsWith("(", e) -> TokenKind.FUNCTION
                        else -> null
                    }
                }
            } ?: continue
            out.add(Token(s, e, kind))
        }
        return out
    }
}
