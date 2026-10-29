package io.github.yilink1.hyperselect

import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.net.Uri
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.FileProvider
import java.io.File

/** 打开方式：打开文件 / 链接时弹出的应用解析列表。 */
class OpenWithActionActivity : ManageListActivity() {

    override val screenTitle: String by lazy { getString(R.string.title_open_with) }
    override val emptyText: String by lazy { getString(R.string.empty_no_apps_available_open_with) }
    override val enablePinnedSection = true

    @Suppress("DEPRECATION")
    override fun queryTargets(pm: PackageManager): List<ResolveInfo> {
        val probes = listOf(
            // 网页与在线链接
            Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com")),
            Intent(Intent.ACTION_VIEW, Uri.parse("http://example.com")),

            // content:// 方案探针（注意：必须使用 setDataAndType，不能调用 setType，否则会清除 Uri scheme）
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

            // file:// 方案探针
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

            // 纯 scheme 探针
            Intent(Intent.ACTION_VIEW, Uri.parse("content://dummy/file.apk")),
            Intent(Intent.ACTION_VIEW, Uri.parse("content://dummy/file.pdf")),
            Intent(Intent.ACTION_VIEW, Uri.parse("content://dummy/file")),
            Intent(Intent.ACTION_VIEW, Uri.parse("file:///dummy/file")),

            // 纯 MIME / 通配探针
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
    override fun orderOf(config: RuleConfig): List<String> = config.openWithCustomOrder
    override fun withRules(config: RuleConfig, blacklist: Set<String>, order: List<String>): RuleConfig =
        config.copy(openWithBlacklist = blacklist, openWithCustomOrder = order)

    override fun testActions(): List<Pair<String, () -> Unit>> =
        listOf(Pair(getString(R.string.action_test_effect), { showTestDialog() }))

    private fun showTestDialog() {
        val options = arrayOf(
            getString(R.string.test_open_web),
            getString(R.string.test_open_text),
            getString(R.string.test_open_link)
        )
        AlertDialog.Builder(this)
            .setTitle(R.string.test_open_dialog_title)
            .setItems(options) { _, which ->
                runCatching {
                    val testIntent = when (which) {
                        0 -> {
                            val testFile = File(cacheDir, "hyper_test.html").apply {
                                writeText("<!DOCTYPE html><html><head><meta charset=\"utf-8\"><title>HyperSelect</title></head><body><h3>HyperSelect 打开方式测试</h3><p>用于验证浏览器排序与应用隐藏效果。</p></body></html>")
                            }
                            val fileUri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", testFile)
                            Intent(Intent.ACTION_VIEW).apply {
                                setDataAndType(fileUri, "text/html")
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                        }
                        1 -> {
                            val testFile = File(cacheDir, "hyper_test.txt").apply {
                                writeText("HyperSelect 打开方式测试文本内容\n用于验证已隐藏应用与排序规则。")
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
                        0 -> getString(R.string.test_open_action_web)
                        1 -> getString(R.string.test_open_action_text)
                        else -> getString(R.string.test_open_action_link)
                    }
                    val chooser = Intent.createChooser(testIntent, chooserTitle)
                    startActivity(chooser)
                }.onFailure { e ->
                    Toast.makeText(this, getString(R.string.test_open_failed, e.message ?: ""), Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }
}
