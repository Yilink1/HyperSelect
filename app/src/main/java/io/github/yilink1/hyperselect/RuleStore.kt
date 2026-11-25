package io.github.yilink1.hyperselect

import android.content.Context
import android.content.SharedPreferences
import android.os.SystemClock
import kotlin.math.abs

object RuleStore {
    private const val PREF_NAME = "hyper_select_rules"
    private const val KEY_CONFIG = "rule_config_json"
    private const val KEY_LAST_SYSTEM_READ_BOOT = "last_system_read_boot"
    private const val KEY_QS_TILES = "qs_tiles"
    private const val KEY_QS_TILES_SYNC_BOOT = "qs_tiles_sync_boot"

    // 开机时间估算的容忍误差（覆盖 NTP 校时漂移；跨重启必然超过该值）
    private const val BOOT_TIME_TOLERANCE_MS = 10_000L

    fun getPrefs(context: Context): SharedPreferences {
        // Use device protected storage if available to ensure availability across boot
        val deContext = runCatching {
            context.createDeviceProtectedStorageContext()
        }.getOrDefault(context)
        return deContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    fun load(context: Context): RuleConfig {
        val json = getPrefs(context).getString(KEY_CONFIG, null)
        return RuleConfig.fromJson(json)
    }

    fun save(context: Context, config: RuleConfig) {
        getPrefs(context).edit()
            .putString(KEY_CONFIG, config.toJson())
            .apply()
    }

    /** 由 ConfigProvider 在被 system_server 读取规则时调用，记录生效凭证。 */
    fun saveSystemReadReceipt(context: Context) {
        val bootTime = System.currentTimeMillis() - SystemClock.elapsedRealtime()
        getPrefs(context).edit()
            .putLong(KEY_LAST_SYSTEM_READ_BOOT, bootTime)
            .apply()
    }

    /** system_server 代读的快捷设置磁贴顺序（Settings.Secure.sysui_qs_tiles）。 */
    fun saveQsTiles(context: Context, tiles: String) {
        saveQsTiles(context, tiles, currentBootTime())
    }

    /** 保存 system_server 本次开机确认过的磁贴列表。 */
    fun saveSystemQsTiles(context: Context, tiles: String) {
        saveQsTiles(context, tiles, currentBootTime())
    }

    private fun saveQsTiles(context: Context, tiles: String, bootTime: Long) {
        getPrefs(context).edit()
            .putString(KEY_QS_TILES, tiles)
            .putLong(KEY_QS_TILES_SYNC_BOOT, bootTime)
            .apply()
    }

    fun loadQsTiles(context: Context): String = getPrefs(context).getString(KEY_QS_TILES, null).orEmpty()

    /** Return cached tiles only when they were confirmed or written during this boot. */
    fun loadQsTilesSyncedThisBoot(context: Context): String? {
        val prefs = getPrefs(context)
        val storedBoot = prefs.getLong(KEY_QS_TILES_SYNC_BOOT, -1L)
        if (storedBoot < 0L || abs(currentBootTime() - storedBoot) > BOOT_TIME_TOLERANCE_MS) return null
        return prefs.getString(KEY_QS_TILES, null)?.takeIf { it.isNotBlank() }
    }

    private fun currentBootTime(): Long = System.currentTimeMillis() - SystemClock.elapsedRealtime()

    /**
     * 模块自检：system_server 是否在【本次开机】后来读取过规则。
     * 是 ⟺ Hook 已加载并正在工作；否则说明模块未激活或激活后未重启。
     */
    fun isModuleActiveSinceBoot(context: Context): Boolean {
        val storedBoot = getPrefs(context).getLong(KEY_LAST_SYSTEM_READ_BOOT, -1L)
        if (storedBoot < 0L) return false
        val currentBoot = currentBootTime()
        return abs(currentBoot - storedBoot) <= BOOT_TIME_TOLERANCE_MS
    }
}
