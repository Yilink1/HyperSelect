package io.github.yilink1.hyperselect

import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.view.View
import android.view.ViewGroup

/** Text Selection：Action items shown when long-pressing text。 */
class TextActionActivity : ManageListActivity() {

    override val screenTitle: String get() = getString(R.string.title_text_selection)
    override val emptyText: String get() = getString(R.string.no_apps_text)
    override val sortByLabelWhenNoorder = false

    private var switchBlockAiWriting: HyperSwitch? = null
    private var rowBlockAiWriting: View? = null

    @Suppress("DEPRECATION")
    override fun queryTargets(pm: PackageManager): List<ResolveInfo> {
        val intent = Intent(Intent.ACTION_PROCESS_TEXT).setType("text/plain")
        intent.putExtra("io.github.yilink1.hyperselect.EXTRA_RAW_QUERY", true)
        return pm.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
    }

    override fun blacklistOf(config: RuleConfig): Set<String> = config.blacklist
    override fun orderOf(config: RuleConfig): List<String> = config.customorder
    override fun withRules(config: RuleConfig, blacklist: Set<String>, order: List<String>): RuleConfig =
        config.copy(blacklist = blacklist, customorder = order)

    override fun onCreateExtra(container: ViewGroup) {
        val row = layoutInflater.inflate(R.layout.item_setting_switch, container, false)
        container.addView(row)
        container.visibility = View.VISIBLE

        val switchView = row.findViewById<HyperSwitch>(R.id.switchBlockAiWriting)
        switchBlockAiWriting = switchView
        rowBlockAiWriting = row

        // Cannot operate until config is loaded to avoid overwriting with defaults
        row.isEnabled = false
        switchView.isEnabled = false

        row.setOnClickListener { switchView.toggle() }
        switchView.onToggled = { saveRules() }
    }

    override fun onConfigLoaded(config: RuleConfig) {
        val switchView = switchBlockAiWriting ?: return
        switchView.setChecked(config.blockAiWriting, animate = false)
        switchView.isEnabled = true
        rowBlockAiWriting?.isEnabled = true
    }

    override fun applyExtra(config: RuleConfig): RuleConfig =
        config.copy(blockAiWriting = switchBlockAiWriting?.checked ?: config.blockAiWriting)
}
