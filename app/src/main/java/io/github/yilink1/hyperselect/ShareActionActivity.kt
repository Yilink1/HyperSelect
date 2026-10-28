package io.github.yilink1.hyperselect

import android.content.Intent
import android.content.pm.Package Manager
import android.content.pm.Resolve Info
import android.widget.Toast
import androidx.appcompat.app.Alert Dialog
import androidx.core.content.File Provider
import java.io.File

/** System Share：Candidate app list when apps invoke the share panel。 */
class Share Action Activity : Manage List Activity() {

    override val screen Title = "System Share"
    override val empty Text = "None Availableof the Share apps"
    override val enable Pinned Section = true

    @Suppress("DEPRECATION")
    override fun query Targets(pm: Package Manager): List<Resolve Info> {
        val send Intent = Intent(Intent.ACTION_SEND).set Type("*/*")
        val send Multiple Intent = Intent(Intent.ACTION_SEND_MULTIPLE).set Type("*/*")
        return pm.query Intent Activities(send Intent, Package Manager.MATCH_ALL) +
            pm.query Intent Activities(send Multiple Intent, Package Manager.MATCH_ALL)
    }

    override fun blacklist Of(config: Rule Config): Set<String> = config.share Blacklist
    override fun order Of(config: Rule Config): List<String> = config.share Customorder
    override fun with Rules(config: Rule Config, blacklist: Set<String>, order: List<String>): Rule Config =
        config.copy(share Blacklist = blacklist, share Customorder = order)

    override fun test Actions(): List<Pair<String, () -> Unit>> =
        list Of(Pair("Test", { show Test Dialog() }))

    private fun show Test Dialog() {
        val options = array Of("Share plain text", "Share test text file")
        Alert Dialog.Builder(this)
            .set Title("Select System Share test type")
            .set Items(options) { _, which ->
                run Catching {
                    val send Intent = if (which == 0) {
                        Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            put Extra(Intent.EXTRA_TEXT, "Hyper Select Share Test Text: This is test content to verify share panel hide and reorder effects.")
                        }
                    } else {
                        val test File = File(cache Dir, "hyper_share_test.txt").apply {
                            write Text("Hyper Select share file test for verifying file sharing  appsof thehideand Sort。")
                        }
                        val file Uri = File Provider.get Uri For File(this, "${package Name}.fileprovider", test File)
                        Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            put Extra(Intent.EXTRA_STREAM, file Uri)
                            add Flags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                    }
                    start Activity(Intent.create Chooser(send Intent, "Hyper Select Share Effect Test"))
                }.on Failure { e ->
                    Toast.make Text(this, " Share : ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
            .show()
    }
}
