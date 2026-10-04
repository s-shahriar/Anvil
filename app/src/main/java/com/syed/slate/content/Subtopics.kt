package com.syed.slate.content

import org.json.JSONObject

class Subtopic(val slug: String, val name: String)

object Subtopics {
    /** Slug used for questions that have no (known) sub-topic. */
    const val NONE = "__none__"
    const val NONE_NAME = "অন্যান্য"

    /** The sub-topic rows for one topic, ordered by the admin's `sort_order`. */
    fun forTopic(rows: List<JSONObject>, topicSlug: String): List<Subtopic> =
        rows.filter { it.optJSONObject("categories")?.optString("slug") == topicSlug }
            .sortedBy { it.optInt("sort_order") }
            .map { Subtopic(it.getString("slug"), it.getString("name")) }

    /** Which sub-topic a question belongs to, if any. */
    fun of(item: Item): String? = item.data.optJSONObject("extra")?.optString("subtopic")?.takeIf { it.isNotEmpty() }

    /** Cards for the picker: each sub-topic with its question count (empty ones hidden), then "অন্যান্য" for the rest. */
    fun cards(items: List<Item>, list: List<Subtopic>): List<Pair<Subtopic, Int>> {
        val known = list.map { it.slug }.toSet()
        val counts = items.groupingBy { of(it) }.eachCount()
        val named = list.map { it to (counts[it.slug] ?: 0) }
        val other = items.count { of(it) !in known }
        return (named + (Subtopic(NONE, NONE_NAME) to other)).filter { it.second > 0 }
    }

    fun inSubtopic(item: Item, slug: String, list: List<Subtopic>): Boolean =
        if (slug == NONE) of(item) !in list.map { it.slug }.toSet() else of(item) == slug
}
