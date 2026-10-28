package io.github.yilink1.hyperselect

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Binder
import android.os.Bundle
import android.os.Process

class ConfigProvider : ContentProvider() {

    companion object {
        const val AUTHORITY = "io.github.yilink1.hyperselect.provider"
        val CONTENT_URI: Uri = Uri.parse("content://$AUTHORITY/rules")

        const val METHOD_GET_RULES = "getRules"
        const val METHOD_NOTIFY_CHANGED = "notifyChanged"
        const val EXTRA_RULES_JSON = "rules_json"
    }

    override fun onCreate(): Boolean = true

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        val ctx = context ?: return null
        return when (method) {
            METHOD_GET_RULES -> {
                // Only log reads from system_server as proof the module is active this boot
                if (Binder.getCallingUid() == Process.SYSTEM_UID) {
                    RuleStore.saveSystemReadReceipt(ctx)
                }
                val config = RuleStore.load(ctx)
                Bundle().apply {
                    putString(EXTRA_RULES_JSON, config.toJson())
                }
            }
            METHOD_NOTIFY_CHANGED -> {
                ctx.contentResolver.notifyChange(CONTENT_URI, null)
                Bundle().apply { putBoolean("result", true) }
            }
            else -> super.call(method, arg, extras)
        }
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? = null

    override fun getType(uri: Uri): String? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?
    ): Int = 0
}
