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
    val tiles: String
) {
    fun toJson(): JSONObject {
        val obj = JSONObject()
        obj.put("id", id)
        obj.put("name", name)
        obj.put("timestamp", timestamp)
        obj.put("tiles", tiles)
        return obj
    }

    companion object {
        fun fromJson(obj: JSONObject): TileBackupItem {
            return TileBackupItem(
                id = obj.optString("id", UUID.randomUUID().toString()),
                name = obj.optString("name", "未命名快照"),
                timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
                tiles = obj.optString("tiles", "")
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
        "batterysaver" to "省电模式",
        "rotation" to "方向锁定",
        "hotspot" to "热点",
        "nfc" to "NFC",
        "airplane" to "飞行模式",
        "papermode" to "护眼模式",
        "gps" to "定位服务",
        "quietmode" to "勿扰模式",
        "night" to "深色模式",
        "vowifi1" to "VoWiFi 1",
        "vowifi2" to "VoWiFi 2",
        "reduce_brightness" to "极暗",
        "screenrecorder" to "屏幕录制",
        "aisubtitles" to "实时字幕",
        "freeformhang" to "迷你小窗",
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

    const val BACKUP_TYPE_TILES = "hyperselect_tile_backups"
    const val CURRENT_VERSION = 1

    fun exportToJson(items: List<TileBackupItem>): String {
        val root = JSONObject()
        root.put("type", BACKUP_TYPE_TILES)
        root.put("version", CURRENT_VERSION)
        root.put("timestamp", System.currentTimeMillis())
        val arr = JSONArray()
        items.forEach { arr.put(it.toJson()) }
        root.put("items", arr)
        return root.toString(2)
    }

    fun importFromJson(jsonStr: String): List<TileBackupItem>? {
        return runCatching {
            val trimmed = jsonStr.trim()
            val list = mutableListOf<TileBackupItem>()
            if (trimmed.startsWith("{")) {
                val root = JSONObject(trimmed)
                val arr = root.optJSONArray("items")
                if (arr != null) {
                    for (i in 0 until arr.length()) {
                        list.add(TileBackupItem.fromJson(arr.getJSONObject(i)))
                    }
                }
            } else if (trimmed.startsWith("[")) {
                val arr = JSONArray(trimmed)
                for (i in 0 until arr.length()) {
                    list.add(TileBackupItem.fromJson(arr.getJSONObject(i)))
                }
            }
            if (list.isEmpty()) null else list
        }.getOrNull()
    }

    fun mergeBackups(context: Context, imported: List<TileBackupItem>): Int {
        val current = loadBackups(context)
        val existingTiles = current.map { it.tiles.trim() }.toMutableSet()
        val existingNames = current.map { it.name.trim() }.toMutableSet()
        var addedCount = 0
        for (item in imported) {
            val trimmedTiles = item.tiles.trim()
            if (trimmedTiles.isEmpty() || trimmedTiles in existingTiles) {
                // 内容完全一致或为空,直接去重跳过
                continue
            }
            var newName = item.name.ifBlank { "导入快照" }
            if (newName in existingNames) {
                newName = "$newName (导入)"
            }
            existingNames.add(newName)
            existingTiles.add(trimmedTiles)
            current.add(0, item.copy(id = UUID.randomUUID().toString(), name = newName))
            addedCount++
        }
        if (addedCount > 0) {
            saveBackups(context, current)
        }
        return addedCount
    }
}