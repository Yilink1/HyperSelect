package io.github.yilink1.hyperselect

import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.FileProvider
import java.io.File

/** 系统分享：应用调出分享面板时的候选应用列表。 */
class ShareActionActivity : ManageListActivity() {

    override val screenTitle: String by lazy { getString(R.string.title_system_share) }
    override val emptyText: String by lazy { getString(R.string.empty_no_apps_available_share) }
    override val enablePinnedSection = true

    @Suppress("DEPRECATION")
    override fun queryTargets(pm: PackageManager): List<ResolveInfo> {
        val sendIntent = Intent(Intent.ACTION_SEND).setType("*/*")
        val sendMultipleIntent = Intent(Intent.ACTION_SEND_MULTIPLE).setType("*/*")
        return pm.queryIntentActivities(sendIntent, PackageManager.MATCH_ALL) +
            pm.queryIntentActivities(sendMultipleIntent, PackageManager.MATCH_ALL)
    }

    override fun blacklistOf(config: RuleConfig): Set<String> = config.shareBlacklist
    override fun orderOf(config: RuleConfig): List<String> = config.shareCustomOrder
    override fun withRules(config: RuleConfig, blacklist: Set<String>, order: List<String>): RuleConfig =
        config.copy(shareBlacklist = blacklist, shareCustomOrder = order)

    override fun testActions(): List<Pair<String, () -> Unit>> =
        listOf(Pair(getString(R.string.action_test_effect), { showTestDialog() }))

    private fun showTestDialog() {
        val options = arrayOf(getString(R.string.test_share_plain_text), getString(R.string.test_share_file))
        AlertDialog.Builder(this)
            .setTitle(R.string.test_share_dialog_title)
            .setItems(options) { _, which ->
                runCatching {
                    val sendIntent = if (which == 0) {
                        Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, getString(R.string.test_share_text_content))
                        }
                    } else {
                        val testFile = File(cacheDir, "hyper_share_test.txt").apply {
                            writeText(getString(R.string.test_share_file_content))
                        }
                        val fileUri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", testFile)
                        Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_STREAM, fileUri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                    }
                    startActivity(Intent.createChooser(sendIntent, getString(R.string.test_share_title)))
                }.onFailure { e ->
                    Toast.makeText(this, getString(R.string.test_share_failed, e.message ?: ""), Toast.LENGTH_SHORT).show()
                }
            }
            .show()
    }
}
