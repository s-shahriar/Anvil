package com.syed.anvil.content

import com.syed.anvil.backend.ModuleId
import org.json.JSONObject

/**
 * One question/card. [data] is the module's own payload, untouched:
 *  - general: id-less question fields (question, options, correct_answer, explanation, extra, ...)
 *  - ict: the `payload` jsonb (written/extra/viva cards carry their whole answer there)
 */
const val CACHE_VERSION = 2

class Item(
    val id: String,
    val uid: String?,
    val group: String,
    val topic: String,
    val sort: Int,
    val data: JSONObject,
) {
    val question: String get() = data.optString("question").ifEmpty { data.optString("q") }

    /** A multiple-choice question that can actually be quizzed: has options and a known answer. */
    val isQuizzable: Boolean
        get() = data.optJSONObject("options") != null && data.optString("correct_answer").isNotEmpty() &&
            !data.isNull("correct_answer")
}

data class TopicInfo(val group: String, val slug: String, val name: String, val count: Int) {
    val key get() = "$group/$slug"
}

data class GroupInfo(val key: String, val title: String, val topics: List<TopicInfo>) {
    val count get() = topics.sumOf { it.count }
}

class ModuleContent(
    val module: ModuleId,
    val groups: List<GroupInfo>,
    private val byTopic: Map<String, List<Item>>,
    val syncedAt: Long,
    /** Raw sub-topic rows (general/LiveMCQ), kept so the cache is complete offline. */
    val subtopics: List<JSONObject>,
    /** General's "Written » Data" reference cards and their categories. */
    val writtenCategories: List<JSONObject> = emptyList(),
    val writtenCards: List<JSONObject> = emptyList(),
    /** Format of the offline copy; an older one is topped up with a fresh download. */
    val version: Int = CACHE_VERSION,
) {
    val total: Int get() = groups.sumOf { it.count }

    /** The same content minus the questions with these row ids (the recycle bin hides them this way). */
    fun without(ids: Set<String>): ModuleContent {
        val names = HashMap<String, String>(); val sort = HashMap<String, Int>()
        groups.forEach { g -> g.topics.forEachIndexed { i, t -> names[t.key] = t.name; sort[t.key] = i } }
        return TopicCatalog.build(module, allItems().filter { it.id !in ids }.toList(), names, sort, subtopics, syncedAt, writtenCategories, writtenCards, version)
    }

    /** A topic's sub-topics, in the order the admin set (LiveMCQ only). */
    fun subtopicsFor(topicSlug: String): List<Subtopic> = Subtopics.forTopic(subtopics, topicSlug)
    fun items(group: String, topic: String): List<Item> = byTopic["$group/$topic"].orEmpty()
    fun allItems(): Sequence<Item> = byTopic.values.asSequence().flatten()
}

object TopicCatalog {
    private val groupTitles = mapOf(
        "bangla" to "Bangla Grammar", "english" to "English Grammar", "sahitya" to "Bangla Sahitya",
        "gk" to "GK", "livemcq" to "LiveMCQ", "vocab" to "Vocabulary",
        "mcq" to "MCQ", "written" to "Written", "extra" to "Extra", "viva" to "Viva",
    )
    private val groupOrder = mapOf(
        ModuleId.GENERAL to listOf("bangla", "english", "sahitya", "gk", "livemcq", "vocab"),
        ModuleId.ICT to listOf("mcq", "written", "extra", "viva"),
    )

    /** ICT topic metadata lives in the web app's source, not the database. */
    val ictTopicNames = linkedMapOf(
        "computer_fundamental" to "Computer Fundamentals", "c_programming" to "C Programming",
        "dsa" to "Data Structures & Algorithms", "database" to "Database Systems",
        "digital_logic" to "Digital Logic", "oop" to "Object Oriented Programming",
        "operating_system" to "Operating Systems", "computer_network" to "Computer Networks",
        "information_security" to "Information Security", "linux" to "Linux",
        "microprocessor" to "Microprocessor", "software_engineering" to "Software Engineering",
        "machine_learning" to "Machine Learning", "theory_of_computation" to "Theory of Computation",
        "server" to "Server", "banking" to "Banking", "datacenter" to "Data Center",
        "general_knowledge" to "General Knowledge",
    )

    fun groupTitle(group: String) = groupTitles[group] ?: prettify(group)

    fun topicName(module: ModuleId, slug: String, dbName: String?): String =
        dbName?.takeIf { it.isNotBlank() } ?: ictTopicNames[slug] ?: prettify(slug)

    fun prettify(slug: String) =
        slug.split('_', '-').filter { it.isNotEmpty() }.joinToString(" ") { it.replaceFirstChar(Char::uppercase) }

    /** Groups in app order; topics in the order the source defines them, then by name. */
    fun build(
        module: ModuleId,
        items: List<Item>,
        names: Map<String, String>,
        topicSort: Map<String, Int>,
        subtopics: List<JSONObject>,
        syncedAt: Long,
        writtenCategories: List<JSONObject> = emptyList(),
        writtenCards: List<JSONObject> = emptyList(),
        version: Int = CACHE_VERSION,
    ): ModuleContent {
        val byTopic = items.groupBy { "${it.group}/${it.topic}" }
            .mapValues { (_, v) -> v.sortedByDescending { it.sort } } // newest first, like the web apps
        val order = groupOrder.getValue(module)
        val groupKeys = items.map { it.group }.distinct().sortedBy { order.indexOf(it).let { i -> if (i < 0) 99 else i } }
        val ictOrder = ictTopicNames.keys.toList()
        val groups = groupKeys.map { g ->
            val topics = byTopic.keys.filter { it.startsWith("$g/") }.map { key ->
                val slug = key.substringAfter('/')
                TopicInfo(g, slug, topicName(module, slug, names[key]), byTopic.getValue(key).size)
            }.sortedWith(
                compareBy<TopicInfo>(
                    { topicSort[it.key] ?: ictOrder.indexOf(it.slug).let { i -> if (i < 0) 999 else i } },
                    { it.name },
                ),
            )
            GroupInfo(g, groupTitle(g), topics)
        }
        return ModuleContent(module, groups, byTopic, syncedAt, subtopics, writtenCategories, writtenCards, version)
    }
}
