package io.github.yilink1.hyperselect

import android.content.Intent
import android.content.pm.Package Manager
import android.content.pm.Resolve Info
import android.net.Uri
import android.widget.Toast
import androidx.appcompat.app.Alert Dialog
import androidx.core.content.File Provider
import java.io.File

/** Open with：Open file / App resolver list shown when opening links。 */
class Open With Action Activity : Manage List Activity() {

    override val screen Title = "Open with"
    override val empty Text = "None Availableof the Open with apps"
    override val enable Pinned Section = true

    @Suppress("DEPRECATION")
    override fun query Targets(pm: Package Manager): List<Resolve Info> {
        val probes = list Of(
            // Web & online links
            Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com")),
            Intent(Intent.ACTION_VIEW, Uri.parse("http://example.com")),

            // content:// probe (note: must use set Data And Type, not set Type to avoid clearing Uri scheme)
            Intent(Intent.ACTION_VIEW).apply { set Data And Type(Uri.parse("content://dummy/file.html"), "text/html") },
            Intent(Intent.ACTION_VIEW).apply { set Data And Type(Uri.parse("content://dummy/file.htm"), "text/html") },
            Intent(Intent.ACTION_VIEW).apply { set Data And Type(Uri.parse("content://dummy/file.txt"), "text/plain") },
            Intent(Intent.ACTION_VIEW).apply { set Data And Type(Uri.parse("content://dummy/file.txt"), "text/*") },
            Intent(Intent.ACTION_VIEW).apply { set Data And Type(Uri.parse("content://dummy/file.pdf"), "application/pdf") },
            Intent(Intent.ACTION_VIEW).apply { set Data And Type(Uri.parse("content://dummy/file.pdf"), "application/*") },
            Intent(Intent.ACTION_VIEW).apply { set Data And Type(Uri.parse("content://dummy/file.doc"), "application/msword") },
            Intent(Intent.ACTION_VIEW).apply { set Data And Type(Uri.parse("content://dummy/file.docx"), "application/vnd.openxmlformats-officedocument.wordprocessingml.document") },
            Intent(Intent.ACTION_VIEW).apply { set Data And Type(Uri.parse("content://dummy/file.xls"), "application/vnd.ms-excel") },
            Intent(Intent.ACTION_VIEW).apply { set Data And Type(Uri.parse("content://dummy/file.xlsx"), "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet") },
            Intent(Intent.ACTION_VIEW).apply { set Data And Type(Uri.parse("content://dummy/file.ppt"), "application/vnd.ms-powerpoint") },
            Intent(Intent.ACTION_VIEW).apply { set Data And Type(Uri.parse("content://dummy/file.pptx"), "application/vnd.openxmlformats-officedocument.presentationml.presentation") },
            Intent(Intent.ACTION_VIEW).apply { set Data And Type(Uri.parse("content://dummy/file.zip"), "application/zip") },
            Intent(Intent.ACTION_VIEW).apply { set Data And Type(Uri.parse("content://dummy/file.zip"), "application/x-zip-compressed") },
            Intent(Intent.ACTION_VIEW).apply { set Data And Type(Uri.parse("content://dummy/file.rar"), "application/x-rar-compressed") },
            Intent(Intent.ACTION_VIEW).apply { set Data And Type(Uri.parse("content://dummy/file.7z"), "application/x-7z-compressed") },
            Intent(Intent.ACTION_VIEW).apply { set Data And Type(Uri.parse("content://dummy/file.apk"), "application/vnd.android.package-archive") },
            Intent(Intent.ACTION_VIEW).apply { set Data And Type(Uri.parse("content://dummy/file.apk"), "application/octet-stream") },
            Intent(Intent.ACTION_VIEW).apply { set Data And Type(Uri.parse("content://dummy/file.apks"), "application/octet-stream") },
            Intent(Intent.ACTION_VIEW).apply { set Data And Type(Uri.parse("content://dummy/file.bin"), "application/octet-stream") },
            Intent(Intent.ACTION_VIEW).apply { set Data And Type(Uri.parse("content://dummy/file.jpg"), "image/jpeg") },
            Intent(Intent.ACTION_VIEW).apply { set Data And Type(Uri.parse("content://dummy/file.jpg"), "image/*") },
            Intent(Intent.ACTION_VIEW).apply { set Data And Type(Uri.parse("content://dummy/file.png"), "image/png") },
            Intent(Intent.ACTION_VIEW).apply { set Data And Type(Uri.parse("content://dummy/file.gif"), "image/gif") },
            Intent(Intent.ACTION_VIEW).apply { set Data And Type(Uri.parse("content://dummy/file.mp4"), "video/mp4") },
            Intent(Intent.ACTION_VIEW).apply { set Data And Type(Uri.parse("content://dummy/file.mp4"), "video/*") },
            Intent(Intent.ACTION_VIEW).apply { set Data And Type(Uri.parse("content://dummy/file.mp3"), "audio/mp3") },
            Intent(Intent.ACTION_VIEW).apply { set Data And Type(Uri.parse("content://dummy/file.mp3"), "audio/*") },
            Intent(Intent.ACTION_VIEW).apply { set Data And Type(Uri.parse("content://dummy/file"), "*/*") },

            // file:// probe
            Intent(Intent.ACTION_VIEW).apply { set Data And Type(Uri.parse("file:///dummy/file.html"), "text/html") },
            Intent(Intent.ACTION_VIEW).apply { set Data And Type(Uri.parse("file:///dummy/file.txt"), "text/plain") },
            Intent(Intent.ACTION_VIEW).apply { set Data And Type(Uri.parse("file:///dummy/file.pdf"), "application/pdf") },
            Intent(Intent.ACTION_VIEW).apply { set Data And Type(Uri.parse("file:///dummy/file.apk"), "application/vnd.android.package-archive") },
            Intent(Intent.ACTION_VIEW).apply { set Data And Type(Uri.parse("file:///dummy/file.apk"), "application/octet-stream") },
            Intent(Intent.ACTION_VIEW).apply { set Data And Type(Uri.parse("file:///dummy/file.zip"), "application/zip") },
            Intent(Intent.ACTION_VIEW).apply { set Data And Type(Uri.parse("file:///dummy/file.jpg"), "image/*") },
            Intent(Intent.ACTION_VIEW).apply { set Data And Type(Uri.parse("file:///dummy/file.mp4"), "video/*") },
            Intent(Intent.ACTION_VIEW).apply { set Data And Type(Uri.parse("file:///dummy/file.mp3"), "audio/*") },
            Intent(Intent.ACTION_VIEW).apply { set Data And Type(Uri.parse("file:///dummy/file"), "*/*") },

            // Pure scheme probe
            Intent(Intent.ACTION_VIEW, Uri.parse("content://dummy/file.apk")),
            Intent(Intent.ACTION_VIEW, Uri.parse("content://dummy/file.pdf")),
            Intent(Intent.ACTION_VIEW, Uri.parse("content://dummy/file")),
            Intent(Intent.ACTION_VIEW, Uri.parse("file:///dummy/file")),

            // Pure MIME / Wildcard probe
            Intent(Intent.ACTION_VIEW).set Type("*/*"),
            Intent(Intent.ACTION_VIEW).set Type("text/*"),
            Intent(Intent.ACTION_VIEW).set Type("application/*"),
            Intent(Intent.ACTION_VIEW)
        )
        return probes.flat Map { intent ->
            run Catching { pm.query Intent Activities(intent, Package Manager.MATCH_ALL) }.get Or Default(empty List())
        }
    }

    override fun blacklist Of(config: Rule Config): Set<String> = config.open With Blacklist
    override fun order Of(config: Rule Config): List<String> = config.open With Customorder
    override fun with Rules(config: Rule Config, blacklist: Set<String>, order: List<String>): Rule Config =
        config.copy(open With Blacklist = blacklist, open With Customorder = order)

    override fun test Actions(): List<Pair<String, () -> Unit>> =
        list Of(Pair("Test", { show Test Dialog() }))

    private fun show Test Dialog() {
        val options = array Of(
            "Test web file (HTML / works with all browsers)",
            "Test plain text file (text/plain)",
            "Testonlinelink (Http/Https)"
        )
        Alert Dialog.Builder(this)
            .set Title("Select Open with Test Type")
            .set Items(options) { _, which ->
                run Catching {
                    val test Intent = when (which) {
                        0 -> {
                            val test File = File(cache Dir, "hyper_test.html").apply {
                                write Text("<!DOCTYPE html><html><head><meta charset=\"utf-8\"><title>Hyper Select</title></head><body><h3>Hyper Select Open with Test</h3><p>For Verify Sortand apps Hide Effect。</p></body></html>")
                            }
                            val file Uri = File Provider.get Uri For File(this, "${package Name}.fileprovider", test File)
                            Intent(Intent.ACTION_VIEW).apply {
                                set Data And Type(file Uri, "text/html")
                                add Flags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                        }
                        1 -> {
                            val test File = File(cache Dir, "hyper_test.txt").apply {
                                write Text("Hyper Select Open with Test Textcontent\n For Verify Hide appsand Sort Rules。")
                            }
                            val file Uri = File Provider.get Uri For File(this, "${package Name}.fileprovider", test File)
                            Intent(Intent.ACTION_VIEW).apply {
                                set Data And Type(file Uri, "text/plain")
                                add Flags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                        }
                        else -> {
                            Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com"))
                        }
                    }
                    val chooser Title = when (which) {
                        0 -> "Test Openwebfile"
                        1 -> "Test Open Textfile"
                        else -> "Test Openonlinelink"
                    }
                    val chooser = Intent.create Chooser(test Intent, chooser Title)
                    start Activity(chooser)
                }.on Failure { e ->
                    Toast.make Text(this, " Open with : ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
            .set Negative Button("Cancel", null)
            .show()
    }
}
