package io.github.yilink1.hyperselect

import android.content.Context
import android.content.Shared Preferences
import android.os.System Clock
import kotlin.math.abs

object Rule Store {
    private const val PREF_NAME = "hyper_select_rules"
    private const val KEY_CONFIG = "rule_config_json"
    private const val KEY_LAST_SYSTEM_READ_BOOT = "last_system_read_boot"

    // Tolerance for boot time estimation (override NTP drift; across Rebootmust exceed this value）
    private const val BOOT_TIME_TOLERANCE_MS = 10_000L

    fun get Prefs(context: Context): Shared Preferences {
        // Use device protected storage if available to ensure availability across boot
        val de Context = run Catching {
            context.create Device Protected Storage Context()
        }.get Or Default(context)
        return de Context.get Shared Preferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    fun load(context: Context): Rule Config {
        val json = get Prefs(context).get String(KEY_CONFIG, null)
        return Rule Config.from Json(json)
    }

    fun save(context: Context, config: Rule Config) {
        get Prefs(context).edit()
            .put String(KEY_CONFIG, config.to Json())
            .apply()
    }

    /** Called by Config Provider when system_server reads rules, records activation proof. */
    fun save System Read Receipt(context: Context) {
        val boot Time = System.current Time Millis() - System Clock.elapsed Realtime()
        get Prefs(context).edit()
            .put Long(KEY_LAST_SYSTEM_READ_BOOT, boot Time)
            .apply()
    }

    /**
     * Module self-check：system_server  【 Boot】after rules。
     *   ⟺ Hook  In progress ； Instructions Module or after Reboot。
     */
    fun is Module Active Since Boot(context: Context): Boolean {
        val stored Boot = get Prefs(context).get Long(KEY_LAST_SYSTEM_READ_BOOT, -1L)
        if (stored Boot < 0L) return false
        val current Boot = System.current Time Millis() - System Clock.elapsed Realtime()
        return abs(current Boot - stored Boot) <= BOOT_TIME_TOLERANCE_MS
    }
}