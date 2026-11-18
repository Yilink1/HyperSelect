package io.github.yilink1.hyperselect

import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.provider.Settings

/** 磁贴管理：查看、搜索与收纳快捷设置磁贴。 */
class TileManageActivity : ManageListActivity() {

    override val screenTitle: String by lazy { getString(R.string.title_tile_manage) }
    override val emptyText: String by lazy { getString(R.string.empty_no_apps_available_tile) }
    override val sortByLabelWhenNoOrder = true
    override val enablePinnedSection = false
    override val isTileMode = true

    @Suppress("DEPRECATION")
    override fun queryTargets(pm: PackageManager): List<ResolveInfo> {
        val intent = Intent("android.service.quicksettings.action.QS_TILE")
        val services = pm.queryIntentServices(intent, PackageManager.MATCH_ALL)

        // targetSdk 31+ 无法直接读取 sysui_qs_tiles，失败时使用 system_server 同步来的副本。
        val rawTiles = runCatching {
            Settings.Secure.getString(contentResolver, ConfigProvider.SETTING_QS_TILES)
        }.getOrNull()?.takeIf { it.isNotBlank() } ?: RuleStore.loadQsTiles(this)

        val activeTiles = rawTiles.split(",").map { it.trim() }.filter { it.isNotEmpty() }

        val activeMap = HashMap<String, Int>()
        activeTiles.forEachIndexed { index, spec ->
            if (spec.startsWith("custom(") && spec.endsWith(")")) {
                val inner = spec.substring(7, spec.length - 1)
                activeMap[inner] = index + 1
                val slash = inner.indexOf('/')
                if (slash != -1) {
                    val pkg = inner.substring(0, slash)
                    val cls = inner.substring(slash + 1)
                    val fullCls = if (cls.startsWith(".")) pkg + cls else cls
                    activeMap["$pkg/$fullCls"] = index + 1
                    activeMap[fullCls] = index + 1
                }
            } else {
                activeMap[spec] = index + 1
            }
        }

        return services.map { resolveInfo ->
            val sInfo = resolveInfo.serviceInfo ?: return@map resolveInfo
            val pkg = sInfo.packageName
            val cls = sInfo.name
            val shortCls = if (cls.startsWith(pkg)) cls.substring(pkg.length) else cls
            val key1 = "$pkg/$shortCls"
            val key2 = "$pkg/$cls"

            val orderNum = activeMap[key1] ?: activeMap[key2] ?: activeMap[cls]
            if (orderNum != null) {
                val origLabel = resolveInfo.loadLabel(pm)?.toString()?.takeIf { it.isNotBlank() }
                    ?: sInfo.loadLabel(pm)?.toString()
                    ?: sInfo.name
                resolveInfo.nonLocalizedLabel = "$origLabel  #$orderNum"
            }
            resolveInfo
        }
    }

    override fun blacklistOf(config: RuleConfig): Set<String> = config.tileBlacklist
    override fun orderOf(config: RuleConfig): List<String> = emptyList()

    override fun withRules(config: RuleConfig, blacklist: Set<String>, order: List<String>): RuleConfig =
        config.copy(tileBlacklist = blacklist)

    override fun onCreateExtra(container: android.view.ViewGroup) {
    }
}
