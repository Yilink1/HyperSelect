package io.github.yilink1.hyperselect

import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.FileProvider
import java.io.File

/** System Share：Candidate app list when apps invoke the share panel。 */
class ShareActionActivity : ManageListActivity() {

    override val screenTitle = "System Share"
    override val emptyText = "NoneAvailableof/theShare apps"
    override val enablePinnedSection = true

    @Suppress("DEPRECATION")
    override fun queryTargets(pm: PackageManager): List<ResolveInfo> {
        val sendIntent = Intent(Intent.ACTION_SEND).setType("*/*")
        val sendMultipleIntent = Intent(Intent.ACTION_SEND_MULTIPLE).setType("*/*")
        return pm.queryIntentActivities(sendIntent, PackageManager.MATCH_ALL) +
            pm.queryIntentActivities(sendMultipleIntent, PackageManager.MATCH_ALL)
    }

    override fun blacklistOf(config: RuleConfig): Set<String> = config.shareBlacklist
    override fun orderOf(config: RuleConfig): List<String> = config.shareCustomorder
    override fun withRules(config: RuleConfig, blacklist: Set<String>, order: List<String>): RuleConfig =
        config.copy(shareBlacklist = blacklist, shareCustomorder = order)

    override fun testActions(): List<Pair<String, () -> Unit>> =
        listOf(Pair("Test", { showTestDialog() }))

    private fun showTestDialog() {
        val options = arrayOf("Share plain text", "Share test text file")
        AlertDialog.Builder(this)
            .setTitle("Select System Share test type")
            .setItems(options) { _, which ->
                runCatching {
                    val sendIntent = if (which == 0) {
                        Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, "HyperSelect Share Test Text: This is test content to verify share panel hide and reorder effects.")
                        }
                    } else {
                        val testFile = File(cacheDir, "hyper_share_test.txt").apply {
                            writeText("HyperSelect share file test for verifying file sharing  appsof/thehideandSort。")
                        }
                        val fileUri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", testFile)
                        Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_STREAM, fileUri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                    }
                    startActivity(Intent.createChooser(sendIntent, "HyperSelect ShareEffectTest"))
                }.onFailure { e ->
                    Toast.makeText(this, " Share : ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
            .show()
    }
}
