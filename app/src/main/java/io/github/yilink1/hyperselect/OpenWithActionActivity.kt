package io.github.yilink1.hyperselect

import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.net.Uri
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.FileProvider
import java.io.File

/** Open with：Open file / App resolver list shown when opening links。 */
class OpenWithActionActivity : ManageListActivity() {

    override val screenTitle: String get() = getString(R.string.open_with_title)
    override val emptyText: String get() = getString(R.string.no_apps_open_with)
    override val enablePinnedSection = true

    @Suppress("DEPRECATION")
    override fun queryTargets(pm: PackageManager): List<ResolveInfo> {
        val probes = listOf(
            // Web & online links
            Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com")),
            Intent(Intent.ACTION_VIEW, Uri.parse("http://example.com")),

            // content:// probe (note: must use setDataAndType, not setType to avoid clearing Uri scheme)
            Intent(Intent.ACTION_VIEW).apply { setDataAndType(Uri.parse("content://dummy/file.html"), "text/html") },
            Intent(Intent.ACTION_VIEW).apply { setDataAndType(Uri.parse("content://dummy/file.htm"), "text/html") },
            Intent(Intent.ACTION_VIEW).apply { setDataAndType(Uri.parse("content://dummy/file.txt"), "text/plain") },
            Intent(Intent.ACTION_VIEW).apply { setDataAndType(Uri.parse("content://dummy/file.txt"), "text/*") },
            Intent(Intent.ACTION_VIEW).apply { setDataAndType(Uri.parse("content://dummy/file.pdf"), "application/pdf") },
            Intent(Intent.ACTION_VIEW).apply { setDataAndType(Uri.parse("content://dummy/file.pdf"), "application/*") },
            Intent(Intent.ACTION_VIEW).apply { setDataAndType(Uri.parse("content://dummy/file.doc"), "application/msword") },
            Intent(Intent.ACTION_VIEW).apply { setDataAndType(Uri.parse("content://dummy/file.docx"), "application/vnd.openxmlformats-officedocument.wordprocessingml.document") },
            Intent(Intent.ACTION_VIEW).apply { setDataAndType(Uri.parse("content://dummy/file.xls"), "application/vnd.ms-excel") },
            Intent(Intent.ACTION_VIEW).apply { setDataAndType(Uri.parse("content://dummy/file.xlsx"), "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet") },
            Intent(Intent.ACTION_VIEW).apply { setDataAndType(Uri.parse("content://dummy/file.ppt"), "application/vnd.ms-powerpoint") },
            Intent(Intent.ACTION_VIEW).apply { setDataAndType(Uri.parse("content://dummy/file.pptx"), "application/vnd.openxmlformats-officedocument.presentationml.presentation") },
            Intent(Intent.ACTION_VIEW).apply { setDataAndType(Uri.parse("content://dummy/file.zip"), "application/zip") },
            Intent(Intent.ACTION_VIEW).apply { setDataAndType(Uri.parse("content://dummy/file.zip"), "application/x-zip-compressed") },
            Intent(Intent.ACTION_VIEW).apply { setDataAndType(Uri.parse("content://dummy/file.rar"), "application/x-rar-compressed") },
            Intent(Intent.ACTION_VIEW).apply { setDataAndType(Uri.parse("content://dummy/file.7z"), "application/x-7z-compressed") },
            Intent(Intent.ACTION_VIEW).apply { setDataAndType(Uri.parse("content://dummy/file.apk"), "application/vnd.android.package-archive") },
            Intent(Intent.ACTION_VIEW).apply { setDataAndType(Uri.parse("content://dummy/file.apk"), "application/octet-stream") },
            Intent(Intent.ACTION_VIEW).apply { setDataAndType(Uri.parse("content://dummy/file.apks"), "application/octet-stream") },
            Intent(Intent.ACTION_VIEW).apply { setDataAndType(Uri.parse("content://dummy/file.bin"), "application/octet-stream") },
            Intent(Intent.ACTION_VIEW).apply { setDataAndType(Uri.parse("content://dummy/file.jpg"), "image/jpeg") },
            Intent(Intent.ACTION_VIEW).apply { setDataAndType(Uri.parse("content://dummy/file.jpg"), "image/*") },
            Intent(Intent.ACTION_VIEW).apply { setDataAndType(Uri.parse("content://dummy/file.png"), "image/png") },
            Intent(Intent.ACTION_VIEW).apply { setDataAndType(Uri.parse("content://dummy/file.gif"), "image/gif") },
            Intent(Intent.ACTION_VIEW).apply { setDataAndType(Uri.parse("content://dummy/file.mp4"), "video/mp4") },
            Intent(Intent.ACTION_VIEW).apply { setDataAndType(Uri.parse("content://dummy/file.mp4"), "video/*") },
            Intent(Intent.ACTION_VIEW).apply { setDataAndType(Uri.parse("content://dummy/file.mp3"), "audio/mp3") },
            Intent(Intent.ACTION_VIEW).apply { setDataAndType(Uri.parse("content://dummy/file.mp3"), "audio/*") },
            Intent(Intent.ACTION_VIEW).apply { setDataAndType(Uri.parse("content://dummy/file"), "*/*") },

            // file:// probe
            Intent(Intent.ACTION_VIEW).apply { setDataAndType(Uri.parse("file:///dummy/file.html"), "text/html") },
            Intent(Intent.ACTION_VIEW).apply { setDataAndType(Uri.parse("file:///dummy/file.txt"), "text/plain") },
            Intent(Intent.ACTION_VIEW).apply { setDataAndType(Uri.parse("file:///dummy/file.pdf"), "application/pdf") },
            Intent(Intent.ACTION_VIEW).apply { setDataAndType(Uri.parse("file:///dummy/file.apk"), "application/vnd.android.package-archive") },
            Intent(Intent.ACTION_VIEW).apply { setDataAndType(Uri.parse("file:///dummy/file.apk"), "application/octet-stream") },
            Intent(Intent.ACTION_VIEW).apply { setDataAndType(Uri.parse("file:///dummy/file.zip"), "application/zip") },
            Intent(Intent.ACTION_VIEW).apply { setDataAndType(Uri.parse("file:///dummy/file.jpg"), "image/*") },
            Intent(Intent.ACTION_VIEW).apply { setDataAndType(Uri.parse("file:///dummy/file.mp4"), "video/*") },
            Intent(Intent.ACTION_VIEW).apply { setDataAndType(Uri.parse("file:///dummy/file.mp3"), "audio/*") },
            Intent(Intent.ACTION_VIEW).apply { setDataAndType(Uri.parse("file:///dummy/file"), "*/*") },

            // Pure scheme probe
            Intent(Intent.ACTION_VIEW, Uri.parse("content://dummy/file.apk")),
            Intent(Intent.ACTION_VIEW, Uri.parse("content://dummy/file.pdf")),
            Intent(Intent.ACTION_VIEW, Uri.parse("content://dummy/file")),
            Intent(Intent.ACTION_VIEW, Uri.parse("file:///dummy/file")),

            // Pure MIME / Wildcard probe
            Intent(Intent.ACTION_VIEW).setType("*/*"),
            Intent(Intent.ACTION_VIEW).setType("text/*"),
            Intent(Intent.ACTION_VIEW).setType("application/*"),
            Intent(Intent.ACTION_VIEW)
        )
        return probes.flatMap { intent ->
            runCatching { pm.queryIntentActivities(intent, PackageManager.MATCH_ALL) }.getOrDefault(emptyList())
        }
    }

    override fun blacklistOf(config: RuleConfig): Set<String> = config.openWithBlacklist
    override fun orderOf(config: RuleConfig): List<String> = config.openWithCustomorder
    override fun withRules(config: RuleConfig, blacklist: Set<String>, order: List<String>): RuleConfig =
        config.copy(openWithBlacklist = blacklist, openWithCustomorder = order)

    override fun testActions(): List<Pair<String, () -> Unit>> =
        listOf(Pair(getString(R.string.test), { showTestDialog() }))

    private fun showTestDialog() {
        val options = arrayOf(
            getString(R.string.test_web_file),
            getString(R.string.test_plain_text),
            getString(R.string.test_online_link)
        )
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.open_with_select_type))
            .setItems(options) { _, which ->
                runCatching {
                    val testIntent = when (which) {
                        0 -> {
                            val testFile = File(cacheDir, "hyper_test.html").apply {
                                writeText("<!DOCTYPE html><html><head><meta charset=\"utf-8\"><title>HyperSelect</title></head><body><h3>HyperSelect Open With test</h3><p>To verify sort and hide effects.</p></body></html>")
                            }
                            val fileUri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", testFile)
                            Intent(Intent.ACTION_VIEW).apply {
                                setDataAndType(fileUri, "text/html")
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                        }
                        1 -> {
                            val testFile = File(cacheDir, "hyper_test.txt").apply {
                                writeText("HyperSelect Open With test text content\nTo verify hidden apps and sort rules.")
                            }
                            val fileUri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", testFile)
                            Intent(Intent.ACTION_VIEW).apply {
                                setDataAndType(fileUri, "text/plain")
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                        }
                        else -> {
                            Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com"))
                        }
                    }
                    val chooserTitle = when (which) {
                        0 -> getString(R.string.test_open_web)
                        1 -> getString(R.string.test_open_text)
                        else -> getString(R.string.test_open_link)
                    }
                    val chooser = Intent.createChooser(testIntent, chooserTitle)
                    startActivity(chooser)
                }.onFailure { e ->
                    Toast.makeText(this, getString(R.string.open_with_failed, e.message), Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }
}
