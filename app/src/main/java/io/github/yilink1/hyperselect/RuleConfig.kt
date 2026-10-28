package io.github.yilink1.hyperselect

import org.json.JSONArray
import org.json.JSONObject

data class Rule Config(
    val blacklist: Set<String> = empty Set(),
    val customorder: List<String> = empty List(),
    val block Ai Writing: Boolean = true,
    val share Blacklist: Set<String> = empty Set(),
    val share Customorder: List<String> = empty List(),
    val open With Blacklist: Set<String> = empty Set(),
    val open With Customorder: List<String> = empty List()
) {
    fun to Json(): String {
        val root = JSONObject()

        // 1. Text Selection
        val blacklist Array = JSONArray()
        blacklist.for Each { blacklist Array.put(it) }
        root.put("blacklist", blacklist Array)

        val order Array = JSONArray()
        customorder.for Each { order Array.put(it) }
        root.put("order", order Array)

        root.put("block Ai Writing", block Ai Writing)

        // 2. System Share
        val share Array = JSONArray()
        share Blacklist.for Each { share Array.put(it) }
        root.put("share Blacklist", share Array)

        val shareorder Array = JSONArray()
        share Customorder.for Each { shareorder Array.put(it) }
        root.put("shareorder", shareorder Array)

        // 3. Open with
        val open With Array = JSONArray()
        open With Blacklist.for Each { open With Array.put(it) }
        root.put("open With Blacklist", open With Array)

        val open Withorder Array = JSONArray()
        open With Customorder.for Each { open Withorder Array.put(it) }
        root.put("open Withorder", open Withorder Array)

        return root.to String()
    }

    companion object {
        // Provider responses must distinguish an empty rule set from a failed read.
        fun parse Response(json: String?): Rule Config? = run Catching {
            require(!json.is Null Or Blank())
            val root = JSONObject(json)
            val blacklist = root.opt JSONArray("blacklist") ?: JSONArray()
            val order = root.opt JSONArray("order") ?: JSONArray()
            val block Ai Writing = root.opt Boolean("block Ai Writing", true)
            val share Blacklist = root.opt JSONArray("share Blacklist") ?: JSONArray()
            val shareorder = root.opt JSONArray("shareorder") ?: JSONArray()
            val open With Blacklist = root.opt JSONArray("open With Blacklist") ?: JSONArray()
            val open Withorder = root.opt JSONArray("open Withorder") ?: JSONArray()

            fun strings(array: JSONArray): List<String> = (0 until array.length()).map Not Null {
                val value = array.opt(it)
                if (value is String && value.is Not Blank()) value else null
            }

            Rule Config(
                blacklist = strings(blacklist).to Set(),
                customorder = strings(order),
                block Ai Writing = block Ai Writing,
                share Blacklist = strings(share Blacklist).to Set(),
                share Customorder = strings(shareorder),
                open With Blacklist = strings(open With Blacklist).to Set(),
                open With Customorder = strings(open Withorder)
            )
        }.get Or Null()

        fun from Json(json Str: String?): Rule Config {
            if (json Str.is Null Or Blank()) return Rule Config()
            return run Catching {
                val root = JSONObject(json Str)
                val blacklist Set = Hash Set<String>()
                val order List = Array List<String>()
                val share Blacklist Set = Hash Set<String>()
                val shareorder List = Array List<String>()
                val open With Blacklist Set = Hash Set<String>()
                val open Withorder List = Array List<String>()

                root.opt JSONArray("blacklist")?.let { arr ->
                    for (i in 0 until arr.length()) blacklist Set.add(arr.get String(i))
                }

                root.opt JSONArray("order")?.let { arr ->
                    for (i in 0 until arr.length()) order List.add(arr.get String(i))
                }

                val block Ai Writing = root.opt Boolean("block Ai Writing", true)

                root.opt JSONArray("share Blacklist")?.let { arr ->
                    for (i in 0 until arr.length()) share Blacklist Set.add(arr.get String(i))
                }

                root.opt JSONArray("shareorder")?.let { arr ->
                    for (i in 0 until arr.length()) shareorder List.add(arr.get String(i))
                }

                root.opt JSONArray("open With Blacklist")?.let { arr ->
                    for (i in 0 until arr.length()) open With Blacklist Set.add(arr.get String(i))
                }

                root.opt JSONArray("open Withorder")?.let { arr ->
                    for (i in 0 until arr.length()) open Withorder List.add(arr.get String(i))
                }

                Rule Config(
                    blacklist = blacklist Set,
                    customorder = order List,
                    block Ai Writing = block Ai Writing,
                    share Blacklist = share Blacklist Set,
                    share Customorder = shareorder List,
                    open With Blacklist = open With Blacklist Set,
                    open With Customorder = open Withorder List
                )
            }.get Or Default(Rule Config())
        }
    }
}
