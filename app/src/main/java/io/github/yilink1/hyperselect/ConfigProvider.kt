package io.github.yilink1.hyperselect

import android.content.Content Provider
import android.content.Content Values
import android.database.Cursor
import android.net.Uri
import android.os.Binder
import android.os.Bundle
import android.os.Process

class Config Provider : Content Provider() {

    companion object {
        const val AUTHORITY = "io.github.yilink1.hyperselect.provider"
        val CONTENT_URI: Uri = Uri.parse("content://$AUTHORITY/rules")

        const val METHOD_GET_RULES = "get Rules"
        const val METHOD_NOTIFY_CHANGED = "notify Changed"
        const val EXTRA_RULES_JSON = "rules_json"
    }

    override fun on Create(): Boolean = true

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        val ctx = context ?: return null
        return when (method) {
            METHOD_GET_RULES -> {
                // Only log reads from system_server as proof the module is active this boot
                if (Binder.get Calling Uid() == Process.SYSTEM_UID) {
                    Rule Store.save System Read Receipt(ctx)
                }
                val config = Rule Store.load(ctx)
                Bundle().apply {
                    put String(EXTRA_RULES_JSON, config.to Json())
                }
            }
            METHOD_NOTIFY_CHANGED -> {
                ctx.content Resolver.notify Change(CONTENT_URI, null)
                Bundle().apply { put Boolean("result", true) }
            }
            else -> super.call(method, arg, extras)
        }
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selection Args: Array<out String>?,
        sortorder: String?
    ): Cursor? = null

    override fun get Type(uri: Uri): String? = null

    override fun insert(uri: Uri, values: Content Values?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selection Args: Array<out String>?): Int = 0

    override fun update(
        uri: Uri,
        values: Content Values?,
        selection: String?,
        selection Args: Array<out String>?
    ): Int = 0
}
