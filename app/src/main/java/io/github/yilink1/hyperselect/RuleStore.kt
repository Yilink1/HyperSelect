package io.github.yilink1.hyperselect

import android.content.Context
import android.content.SharedPreferences
import android.os.SystemClock
import kotlin.math.abs

object RuleStore {
    private const val PREF_NAME = "hyper_select_rules"
    private const val KEY_CONFIG = "rule_config_json"
    private const val KEY_LAST_SYSTEM_READ_BOOT = "last_system_read_boot"

    // Tolerance for boot-time estimation (absorbs NTP drift across reboots).
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

    /** Called by ConfigProvider when system_server reads rules, records activation proof. */
    fun saveSystemReadReceipt(context: Context) {
        val bootTime = System.currentTimeMillis() - SystemClock.elapsedRealtime()
        getPrefs(context).edit()
            .putLong(KEY_LAST_SYSTEM_READ_BOOT, bootTime)
            .apply()
    }

    /**
     * Module self-check: system_server has read the rules during this boot.
     * This is equivalent to the hook being active after boot.
     */
    fun isModuleActiveSinceBoot(context: Context): Boolean {
        val storedBoot = getPrefs(context).getLong(KEY_LAST_SYSTEM_READ_BOOT, -1L)
        if (storedBoot < 0L) return false
        val currentBoot = System.currentTimeMillis() - SystemClock.elapsedRealtime()
        return abs(currentBoot - storedBoot) <= BOOT_TIME_TOLERANCE_MS
    }
}