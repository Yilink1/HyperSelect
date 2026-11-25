package io.github.yilink1.hyperselect

import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import java.util.Locale

/** 磁贴管理：搜索、一键添加至控制中心与收纳快捷设置磁贴。 */
class TileManageActivity : ManageListActivity() {

    override val screenTitle: String by lazy { getString(R.string.title_tile_manage) }
    override val emptyText: String by lazy { getString(R.string.empty_no_apps_available_tile) }
    override val sortByLabelWhenNoOrder = true
    override val enablePinnedSection = false
    override val isTileMode = true

    private val activeTilesSet = HashSet<String>()

    @Suppress("DEPRECATION")
    override fun queryTargets(pm: PackageManager): List<ResolveInfo> {
        val intent = Intent("android.service.quicksettings.action.QS_TILE")
        val services = pm.queryIntentServices(intent, PackageManager.MATCH_ALL)

        val rawTiles = runCatching {
            Settings.Secure.getString(contentResolver, ConfigProvider.SETTING_QS_TILES)
        }.getOrNull()?.takeIf { it.isNotBlank() } ?: RuleStore.loadQsTiles(this)

        activeTilesSet.clear()
        val activeTiles = rawTiles.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        for (spec in activeTiles) {
            if (spec.startsWith("custom(") && spec.endsWith(")")) {
                val inner = spec.substring(7, spec.length - 1)
                activeTilesSet.add(inner)
                val slash = inner.indexOf('/')
                if (slash != -1) {
                    val pkg = inner.substring(0, slash)
                    val cls = inner.substring(slash + 1)
                    val fullCls = if (cls.startsWith(".")) pkg + cls else cls
                    activeTilesSet.add("$pkg/$fullCls")
                    activeTilesSet.add(fullCls)
                }
            } else {
                activeTilesSet.add(spec)
            }
        }

        return services
    }

    override fun isTileActive(pkg: String, cls: String, component: String): Boolean {
        val shortCls = if (cls.startsWith(pkg)) cls.substring(pkg.length) else cls
        return activeTilesSet.contains("$pkg/$shortCls") ||
            activeTilesSet.contains("$pkg/$cls") ||
            activeTilesSet.contains(cls) ||
            activeTilesSet.contains(component)
    }

    override fun onToggleTileQs(item: TextActionItem) {
        val willAdd = !item.isTileAdded
        val spec = "custom(${item.componentName})"
        val success = updateQsTiles(spec, willAdd)
        if (success) {
            item.isTileAdded = willAdd
            if (willAdd) {
                activeTilesSet.add(item.componentName)
            } else {
                activeTilesSet.remove(item.componentName)
            }
            adapter.notifyDataSetChanged()
            val msg = if (willAdd) R.string.tile_added_success else R.string.tile_removed_success
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, R.string.tile_update_failed, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onItemClick(item: TextActionItem) {
        val toggleActionTitle = if (item.isTileAdded) {
            getString(R.string.action_remove_tile)
        } else {
            getString(R.string.action_add_to_qs)
        }

        val desc = buildString {
            if (item.appName.isNotBlank()) append(item.appName).append("\n")
            append(item.componentName)
        }

        AlertDialog.Builder(this)
            .setTitle(item.label)
            .setMessage(desc)
            .setIcon(item.icon)
            .setPositiveButton(R.string.action_launch_app) { _, _ ->
                val launchIntent = packageManager.getLaunchIntentForPackage(item.packageName)
                if (launchIntent != null) {
                    startActivity(launchIntent)
                } else {
                    Toast.makeText(this, R.string.empty_no_apps_available_tile, Toast.LENGTH_SHORT).show()
                }
            }
            .setNeutralButton(toggleActionTitle) { _, _ ->
                onToggleTileQs(item)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun updateQsTiles(spec: String, add: Boolean): Boolean {
        val raw = runCatching {
            Settings.Secure.getString(contentResolver, ConfigProvider.SETTING_QS_TILES)
        }.getOrNull()?.takeIf { it.isNotBlank() } ?: return false

        val tiles = raw.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toMutableList()
        val targetInner = spec.removePrefix("custom(").removeSuffix(")")
        val existingIndex = tiles.indexOfFirst {
            it == spec || it.removePrefix("custom(").removeSuffix(")") == targetInner
        }

        if (add) {
            if (existingIndex >= 0) return true
            if (tiles.lastOrNull() == "edit") {
                tiles.add(tiles.size - 1, spec)
            } else {
                tiles.add(spec)
            }
        } else {
            if (existingIndex < 0) return true
            tiles.removeAt(existingIndex)
        }

        val newTilesStr = tiles.joinToString(",")
        val success = runCatching {
            Settings.Secure.putString(contentResolver, ConfigProvider.SETTING_QS_TILES, newTilesStr)
        }.getOrDefault(false)
        if (success) RuleStore.saveQsTiles(this, newTilesStr)

        return success
    }

    override fun blacklistOf(config: RuleConfig): Set<String> = config.tileBlacklist
    override fun orderOf(config: RuleConfig): List<String> = emptyList()

    override fun withRules(config: RuleConfig, blacklist: Set<String>, order: List<String>): RuleConfig =
        config.copy(tileBlacklist = blacklist)

    override fun onItemStashed(item: TextActionItem) {
        if (item.isTileAdded) {
            val spec = "custom(${item.componentName})"
            updateQsTiles(spec, add = false)
            item.isTileAdded = false
            activeTilesSet.remove(item.componentName)
        }
    }

    override fun onCreateExtra(container: android.view.ViewGroup) {
        btnBackup.visibility = android.view.View.VISIBLE
        btnBackup.setOnClickListener {
            startActivity(Intent(this, TileBackupActivity::class.java))
        }
    }
}
