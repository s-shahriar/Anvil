package com.syed.anvil.practice

import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream

/** ICT » Practice: Linux and SQL command drills. Bundled with the app (they are not in the database). */
class TableData(val columns: List<String>, val rows: List<List<Any?>>)
typealias SampleTables = Map<String, TableData>

class InfoTable(val headers: List<String>?, val rows: List<List<String>>)
class Info(val summary: List<String>, val table: InfoTable?)
class Command(val cmd: String, val desc: String)
class Problem(val prompt: String, val accept: List<String>, val answers: List<String>, val explain: String?)

class Topic(
    val id: String, val name: String, val set: String?, val info: Info?,
    val commands: List<Command>, val practice: List<Problem>, val sampleData: SampleTables?,
)

class Category(
    val id: String, val name: String, val topics: List<Topic>,
    private val sampleData: SampleTables?, private val sampleDataBySet: Map<String, SampleTables>,
) {
    /** SQL answers are compared case-insensitively; shell commands are not. */
    val caseInsensitive get() = id == "sql"

    /** The topic's own tables, else its set's, else the category default (the web's order). */
    fun sampleFor(t: Topic): SampleTables? = t.sampleData ?: t.set?.let { sampleDataBySet[it] } ?: sampleData
}

/** One drill, with everything needed to run and flag it. */
class Drill(val problem: Problem, val id: String, val caseInsensitive: Boolean, val tag: String?, val sample: SampleTables?)

object PracticeData {
    private val order = listOf("linux", "sql")

    fun load(open: (String) -> InputStream): List<Category> = order.map { parseCategory(JSONObject(open("practice/$it.json").bufferedReader().use { r -> r.readText() })) }

    private fun strings(a: JSONArray?): List<String> = a?.let { List(it.length()) { i -> it.optString(i) } }.orEmpty()

    private fun tables(o: JSONObject?): SampleTables? = o?.let { src ->
        src.keys().asSequence().associateWith { name ->
            val t = src.getJSONObject(name)
            val rows = t.getJSONArray("rows")
            TableData(strings(t.getJSONArray("columns")), List(rows.length()) { r -> rows.getJSONArray(r).let { row -> List(row.length()) { c -> if (row.isNull(c)) null else row.get(c) } } })
        }
    }

    private fun parseTopic(t: JSONObject): Topic {
        val info = t.optJSONObject("info")?.let { i ->
            Info(strings(i.optJSONArray("summary")), i.optJSONObject("table")?.takeIf { (it.optJSONArray("rows")?.length() ?: 0) > 0 }?.let { tb ->
                val rows = tb.getJSONArray("rows")
                InfoTable(tb.optJSONArray("headers")?.let(::strings), List(rows.length()) { r -> strings(rows.getJSONArray(r)) })
            })
        }
        val commands = t.optJSONArray("commands")?.let { a -> List(a.length()) { Command(a.getJSONObject(it).optString("cmd"), a.getJSONObject(it).optString("desc")) } }.orEmpty()
        val practice = t.optJSONArray("practice")?.let { a ->
            List(a.length()) { i -> a.getJSONObject(i).let { p -> Problem(p.optString("prompt"), strings(p.optJSONArray("accept")), strings(p.optJSONArray("answers")), p.optString("explain").takeIf { s -> s.isNotEmpty() }) } }
        }.orEmpty()
        return Topic(t.getString("id"), t.getString("name"), t.optString("set").takeIf { it.isNotEmpty() }, info, commands, practice, tables(t.optJSONObject("sampleData")))
    }

    private fun parseCategory(c: JSONObject): Category {
        val topics = c.getJSONArray("topics").let { a -> List(a.length()) { parseTopic(a.getJSONObject(it)) } }
        val bySet = c.optJSONObject("sampleDataBySet")?.let { o -> o.keys().asSequence().associateWith { tables(o.getJSONObject(it))!! } }.orEmpty()
        return Category(c.getString("category"), c.getString("name"), topics, tables(c.optJSONObject("sampleData")), bySet)
    }
}

/** The web's answer-matching and flag-key rules, ported exactly so marks line up with the website. */
object Practice {
    private val spaces = Regex("\\s+")
    private val trailingSemis = Regex("\\s*;+\\s*$")
    private val sqlOps = Regex("\\s*(<=|>=|<>|!=|=|<|>|,|\\(|\\))\\s*")

    /** Trim, collapse whitespace, drop a trailing `;`. With [lower] (SQL) also ignore spacing around operators, and case. */
    fun normalizeCommand(s: String?, lower: Boolean = false): String {
        var v = (s ?: "").trim().replace(spaces, " ").replace(trailingSemis, "")
        if (lower) v = v.replace(sqlOps) { " ${it.groupValues[1]} " }.replace(spaces, " ").trim().lowercase()
        return v
    }

    fun checkAnswer(input: String, accept: List<String>, caseInsensitive: Boolean = false): Boolean {
        val n = normalizeCommand(input, caseInsensitive)
        return accept.any { normalizeCommand(it, caseInsensitive) == n }
    }

    /** `practice__<category>__<topic>__<normalised command>` — keyed by the command, so a drill and its Commands card share one mark. */
    fun cmdId(category: String, topic: String, cmd: String) = "practice__${category}__${topic}__${normalizeCommand(cmd, category == "sql")}"

    class CommandCard(val cmds: List<String>, val desc: String, val prompt: String, val key: String)

    /** Commands tab: curated commands plus one card per drill, de-duplicated by normalised form. */
    fun buildCommandList(commands: List<Command>, practice: List<Problem>): List<CommandCard> {
        val practiceForms = HashSet<String>()
        practice.forEach { p ->
            val primary = p.accept.firstOrNull() ?: return@forEach
            (p.answers.ifEmpty { listOf(primary) }).forEach { practiceForms.add(normalizeCommand(it)) }
        }
        val list = ArrayList<CommandCard>()
        val seenCmd = HashSet<String>()
        commands.forEach { c ->
            if (c.cmd.isEmpty()) return@forEach
            val key = normalizeCommand(c.cmd)
            if (!seenCmd.add(key) || key in practiceForms) return@forEach
            list.add(CommandCard(listOf(c.cmd), c.desc, "", c.cmd))
        }
        val seenDrill = HashSet<String>()
        practice.forEach { p ->
            val primary = p.accept.firstOrNull() ?: return@forEach
            if (!seenDrill.add(normalizeCommand(primary))) return@forEach
            list.add(CommandCard(p.answers.ifEmpty { listOf(primary) }, p.explain.orEmpty(), p.prompt, primary))
        }
        return list
    }

    fun drillsFor(c: Category, t: Topic, tag: Boolean = false): List<Drill> = t.practice.map {
        Drill(it, cmdId(c.id, t.id, it.accept.firstOrNull().orEmpty()), c.caseInsensitive, if (tag) "${c.name} · ${t.name}" else null, c.sampleFor(t))
    }
}
