package io.github.yilink1.hyperselect

import org.json.JSONArray
import org.json.JSONObject

data class RuleConfig(
    val blacklist: Set<String> = emptySet(),
    val customOrder: List<String> = emptyList(),
    val blockAiWriting: Boolean = true,
    val shareBlacklist: Set<String> = emptySet(),
    val shareCustomOrder: List<String> = emptyList(),
    val openWithBlacklist: Set<String> = emptySet(),
    val openWithCustomOrder: List<String> = emptyList()
) {
    fun toJson(): String {
        val root = JSONObject()

        // 1. Text Selection
        val blacklistArray = JSONArray()
        blacklist.forEach { blacklistArray.put(it) }
        root.put("blacklist", blacklistArray)

        val orderArray = JSONArray()
        customOrder.forEach { orderArray.put(it) }
        root.put("order", orderArray)

        root.put("blockAiWriting", blockAiWriting)

        // 2. System Share
        val shareArray = JSONArray()
        shareBlacklist.forEach { shareArray.put(it) }
        root.put("shareBlacklist", shareArray)

        val shareOrderArray = JSONArray()
        shareCustomOrder.forEach { shareOrderArray.put(it) }
        root.put("shareOrder", shareOrderArray)

        // 3. Open with
        val openWithArray = JSONArray()
        openWithBlacklist.forEach { openWithArray.put(it) }
        root.put("openWithBlacklist", openWithArray)

        val openWithOrderArray = JSONArray()
        openWithCustomOrder.forEach { openWithOrderArray.put(it) }
        root.put("openWithOrder", openWithOrderArray)

        return root.toString()
    }

    companion object {
        // Provider responses must distinguish an empty rule set from a failed read.
        fun parseResponse(json: String?): RuleConfig? = runCatching {
            require(!json.isNullOrBlank())
            val root = JSONObject(json)
            val blacklist = root.optJSONArray("blacklist") ?: JSONArray()
            val order = root.optJSONArray("order") ?: JSONArray()
            val blockAiWriting = root.optBoolean("blockAiWriting", true)
            val shareBlacklist = root.optJSONArray("shareBlacklist") ?: JSONArray()
            val shareOrder = root.optJSONArray("shareOrder") ?: JSONArray()
            val openWithBlacklist = root.optJSONArray("openWithBlacklist") ?: JSONArray()
            val openWithOrder = root.optJSONArray("openWithOrder") ?: JSONArray()

            fun strings(array: JSONArray): List<String> = (0 until array.length()).mapNotNull {
                val value = array.opt(it)
                if (value is String && value.isNotBlank()) value else null
            }

            RuleConfig(
                blacklist = strings(blacklist).toSet(),
                customOrder = strings(order),
                blockAiWriting = blockAiWriting,
                shareBlacklist = strings(shareBlacklist).toSet(),
                shareCustomOrder = strings(shareOrder),
                openWithBlacklist = strings(openWithBlacklist).toSet(),
                openWithCustomOrder = strings(openWithOrder)
            )
        }.getOrNull()

        fun fromJson(jsonStr: String?): RuleConfig {
            if (jsonStr.isNullOrBlank()) return RuleConfig()
            return runCatching {
                val root = JSONObject(jsonStr)
                val blacklistSet = HashSet<String>()
                val orderList = ArrayList<String>()
                val shareBlacklistSet = HashSet<String>()
                val shareOrderList = ArrayList<String>()
                val openWithBlacklistSet = HashSet<String>()
                val openWithOrderList = ArrayList<String>()

                root.optJSONArray("blacklist")?.let { arr ->
                    for (i in 0 until arr.length()) blacklistSet.add(arr.getString(i))
                }

                root.optJSONArray("order")?.let { arr ->
                    for (i in 0 until arr.length()) orderList.add(arr.getString(i))
                }

                val blockAiWriting = root.optBoolean("blockAiWriting", true)

                root.optJSONArray("shareBlacklist")?.let { arr ->
                    for (i in 0 until arr.length()) shareBlacklistSet.add(arr.getString(i))
                }

                root.optJSONArray("shareOrder")?.let { arr ->
                    for (i in 0 until arr.length()) shareOrderList.add(arr.getString(i))
                }

                root.optJSONArray("openWithBlacklist")?.let { arr ->
                    for (i in 0 until arr.length()) openWithBlacklistSet.add(arr.getString(i))
                }

                root.optJSONArray("openWithOrder")?.let { arr ->
                    for (i in 0 until arr.length()) openWithOrderList.add(arr.getString(i))
                }

                RuleConfig(
                    blacklist = blacklistSet,
                    customOrder = orderList,
                    blockAiWriting = blockAiWriting,
                    shareBlacklist = shareBlacklistSet,
                    shareCustomOrder = shareOrderList,
                    openWithBlacklist = openWithBlacklistSet,
                    openWithCustomOrder = openWithOrderList
                )
            }.getOrDefault(RuleConfig())
        }
    }
}
