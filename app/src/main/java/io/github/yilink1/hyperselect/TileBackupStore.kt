package io.github.yilink1.hyperselect

import android.content.ComponentName
import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class TileBackupItem(
    val id: String = UUID.randomUUID().toString(),
    var name: String,
    val timestamp: Long = System.currentTimeMillis(),
    val tiles: String,
    val count: Int,
    val previewLabels: List<String>
) {
    fun toJson(): JSONObject {
        val obj = JSONObject()
        obj.put("id", id)
        obj.put("name", name)
        obj.put("timestamp", timestamp)
        obj.put("tiles", tiles)
        obj.put("count", count)
        val arr = JSONArray()
        previewLabels.forEach { arr.put(it) }
        obj.put("previewLabels", arr)
        return obj
    }

    companion object {
        fun fromJson(obj: JSONObject): TileBackupItem {
            val previewList = ArrayList<String>()
            val arr = obj.optJSONArray("previewLabels")
            if (arr != null) {
                for (i in 0 until arr.length()) {
                    previewList.add(arr.optString(i))
                }
            }
            return TileBackupItem(
                id = obj.optString("id", UUID.randomUUID().toString()),
                name = obj.optString("name", "未命名快照"),
                timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
                tiles = obj.optString("tiles", ""),
                count = obj.optInt("count", 0),
                previewLabels = previewList
            )
        }
    }
}

object TileBackupStore {
    private const val PREF_KEY_BACKUPS = "tile_layout_backups"

    private val SYSTEM_TILE_NAMES = mapOf(
        "wifi" to "WLAN",
        "cell" to "移动数据",
        "bt" to "蓝牙",
        "flashlight" to "手电筒",
        "screenshot" to "截屏",
        "mute" to "静音",
        "batterysaver" to "省电",
        "rotation" to "旋转",
        "hotspot" to "热点",
        "nfc" to "NFC",
        "airplane" to "飞行模式",
        "papermode" to "护眼",
        "gps" to "定位",
        "quietmode" to "勿扰",
        "night" to "深色",
        "vowifi1" to "VoWiFi 1",
        "vowifi2" to "VoWiFi 2",
        "reduce_brightness" to "极暗",
        "screenrecorder" to "录屏",
        "aisubtitles" to "AI字幕",
        "freeformhang" to "小窗",
        "taplus_tile" to "传送门"
    )

    fun loadBackups(context: Context): MutableList<TileBackupItem> {
        val jsonStr = RuleStore.getPrefs(context).getString(PREF_KEY_BACKUPS, null) ?: return mutableListOf()
        val list = mutableListOf<TileBackupItem>()
        runCatching {
            val arr = JSONArray(jsonStr)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(TileBackupItem.fromJson(obj))
            }
        }
        return list
    }

    fun saveBackups(context: Context, list: List<TileBackupItem>) {
        val arr = JSONArray()
        list.forEach { arr.put(it.toJson()) }
        RuleStore.getPrefs(context).edit().putString(PREF_KEY_BACKUPS, arr.toString()).apply()
    }

    fun addBackup(context: Context, item: TileBackupItem) {
        val list = loadBackups(context)
        list.add(0, item)
        saveBackups(context, list)
    }

    fun deleteBackup(context: Context, id: String) {
        val list = loadBackups(context)
        list.removeAll { it.id == id }
        saveBackups(context, list)
    }

    fun renameBackup(context: Context, id: String, newName: String) {
        val list = loadBackups(context)
        val target = list.firstOrNull { it.id == id } ?: return
        target.name = newName
        saveBackups(context, list)
    }

    fun parsePreviewLabels(context: Context, tilesStr: String): List<String> {
        val pm = context.packageManager
        val rawList = tilesStr.split(",").map { it.trim() }.filter { it.isNotEmpty() && it != "edit" }
        val labels = mutableListOf<String>()

        for (spec in rawList) {
            if (spec.startsWith("custom(") && spec.endsWith(")")) {
                val inner = spec.substring(7, spec.length - 1)
                val slash = inner.indexOf('/')
                if (slash != -1) {
                    val pkg = inner.substring(0, slash)
                    val cls = inner.substring(slash + 1)
                    val fullCls = if (cls.startsWith(".")) pkg + cls else cls
                    val label = runCatching {
                        val sInfo = pm.getServiceInfo(ComponentName(pkg, fullCls), 0)
                        sInfo.loadLabel(pm)?.toString()
                    }.getOrNull() ?: runCatching {
                        pm.getApplicationInfo(pkg, 0).loadLabel(pm).toString()
                    }.getOrNull() ?: cls.substringAfterLast('.')
                    labels.add(label)
                } else {
                    labels.add(inner.substringAfterLast('.'))
                }
            } else {
                val friendly = SYSTEM_TILE_NAMES[spec] ?: spec
                labels.add(friendly)
            }
        }
        return labels
    }
}
