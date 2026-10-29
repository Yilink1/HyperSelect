package io.github.yilink1.hyperselect

import org.json.JSONArray
import org.json.JSONObject

data class RuleConfig(
    val blacklist: Set<String> = emptySet(),
    val customorder: List<String> = emptyList(),
    val blockAiWriting: Boolean = true,
    val shareBlacklist: Set<String> = emptySet(),
    val shareCustomorder: List<String> = emptyList(),
    val openWithBlacklist: Set<String> = emptySet(),
    val openWithCustomorder: List<String> = emptyList(),
    val moduleEnabled: Boolean = true,
    val textEnabled: Boolean = true,
    val shareEnabled: Boolean = true,
    val openWithEnabled: Boolean = true
) {
    fun toJson(): String {
        val root = JSONObject()

        // 1. Text Selection
        val blacklistArray = JSONArray()
        blacklist.forEach { blacklistArray.put(it) }
        root.put("blacklist", blacklistArray)

        val orderArray = JSONArray()
        customorder.forEach { orderArray.put(it) }
        root.put("order", orderArray)

        root.put("blockAiWriting", blockAiWriting)

        // 2. System Share
        val shareArray = JSONArray()
        shareBlacklist.forEach { shareArray.put(it) }
        root.put("shareBlacklist", shareArray)

        val shareorderArray = JSONArray()
        shareCustomorder.forEach { shareorderArray.put(it) }
        root.put("shareorder", shareorderArray)

        // 3. Open with
        val openWithArray = JSONArray()
        openWithBlacklist.forEach { openWithArray.put(it) }
        root.put("openWithBlacklist", openWithArray)

        val openWithorderArray = JSONArray()
        openWithCustomorder.forEach { openWithorderArray.put(it) }
        root.put("openWithorder", openWithorderArray)

        // 4. Feature toggles
        root.put("moduleEnabled", moduleEnabled)
        root.put("textEnabled", textEnabled)
        root.put("shareEnabled", shareEnabled)
        root.put("openWithEnabled", openWithEnabled)

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
            val shareorder = root.optJSONArray("shareorder") ?: JSONArray()
            val openWithBlacklist = root.optJSONArray("openWithBlacklist") ?: JSONArray()
            val openWithorder = root.optJSONArray("openWithorder") ?: JSONArray()
            val moduleEnabled = root.optBoolean("moduleEnabled", true)
            val textEnabled = root.optBoolean("textEnabled", true)
            val shareEnabled = root.optBoolean("shareEnabled", true)
            val openWithEnabled = root.optBoolean("openWithEnabled", true)

            fun strings(array: JSONArray): List<String> = (0 until array.length()).mapNotNull {
                val value = array.opt(it)
                if (value is String && value.isNotBlank()) value else null
            }

            RuleConfig(
                blacklist = strings(blacklist).toSet(),
                customorder = strings(order),
                blockAiWriting = blockAiWriting,
                shareBlacklist = strings(shareBlacklist).toSet(),
                shareCustomorder = strings(shareorder),
                openWithBlacklist = strings(openWithBlacklist).toSet(),
                openWithCustomorder = strings(openWithorder),
                moduleEnabled = moduleEnabled,
                textEnabled = textEnabled,
                shareEnabled = shareEnabled,
                openWithEnabled = openWithEnabled
            )
        }.getOrNull()

        fun fromJson(jsonStr: String?): RuleConfig {
            if (jsonStr.isNullOrBlank()) return RuleConfig()
            return runCatching {
                val root = JSONObject(jsonStr)
                val blacklistSet = HashSet<String>()
                val orderList = ArrayList<String>()
                val shareBlacklistSet = HashSet<String>()
                val shareorderList = ArrayList<String>()
                val openWithBlacklistSet = HashSet<String>()
                val openWithorderList = ArrayList<String>()

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

                root.optJSONArray("shareorder")?.let { arr ->
                    for (i in 0 until arr.length()) shareorderList.add(arr.getString(i))
                }

                root.optJSONArray("openWithBlacklist")?.let { arr ->
                    for (i in 0 until arr.length()) openWithBlacklistSet.add(arr.getString(i))
                }

                root.optJSONArray("openWithorder")?.let { arr ->
                    for (i in 0 until arr.length()) openWithorderList.add(arr.getString(i))
                }

                val moduleEnabled = root.optBoolean("moduleEnabled", true)
                val textEnabled = root.optBoolean("textEnabled", true)
                val shareEnabled = root.optBoolean("shareEnabled", true)
                val openWithEnabled = root.optBoolean("openWithEnabled", true)

                RuleConfig(
                    blacklist = blacklistSet,
                    customorder = orderList,
                    blockAiWriting = blockAiWriting,
                    shareBlacklist = shareBlacklistSet,
                    shareCustomorder = shareorderList,
                    openWithBlacklist = openWithBlacklistSet,
                    openWithCustomorder = openWithorderList,
                    moduleEnabled = moduleEnabled,
                    textEnabled = textEnabled,
                    shareEnabled = shareEnabled,
                    openWithEnabled = openWithEnabled
                )
            }.getOrDefault(RuleConfig())
        }
    }
}
