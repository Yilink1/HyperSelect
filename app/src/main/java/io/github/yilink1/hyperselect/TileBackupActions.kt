package io.github.yilink1.hyperselect

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog

/** 快照相关的通用操作,列表页和详情页共用 */
object TileBackupActions {

    fun readVerifiedCurrentTiles(context: Context): String? =
        TileSettings.read(context)

    /** Older cached copies may be displayed, but must not be used for mutations. */
    fun readCurrentTiles(context: Context): String? =
        readVerifiedCurrentTiles(context) ?: RuleStore.loadQsTiles(context)

    fun apply(context: Context, item: TileBackupItem): Boolean {
        if (!TileSettings.hasTiles(item.tiles)) {
            Toast.makeText(context, R.string.tile_snapshot_empty, Toast.LENGTH_SHORT).show()
            return false
        }
        val result = TileSettings.update(context, TileSettings.RESTORE, item.tiles)
        val message = when (result) {
            TileSettings.UpdateResult.SUCCESS -> R.string.toast_layout_applied
            TileSettings.UpdateResult.LAYOUT_UNAVAILABLE -> R.string.tile_layout_unavailable
            TileSettings.UpdateResult.MISSING_LAYOUT -> R.string.tile_layout_missing
            TileSettings.UpdateResult.WRITE_FAILED -> R.string.tile_update_failed
        }
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        return result == TileSettings.UpdateResult.SUCCESS
    }

    fun copy(context: Context, item: TileBackupItem) {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("sysui_qs_tiles", item.tiles))
        Toast.makeText(context, R.string.toast_copied_to_clipboard, Toast.LENGTH_SHORT).show()
    }

    fun inputDialog(activity: Activity, title: Int, initial: String, hint: Int? = null,
                    multiLine: Boolean = false, positive: Int = android.R.string.ok,
                    onOk: (String) -> Unit) {
        val et = EditText(activity).apply {
            setText(initial)
            setSelection(initial.length)
            if (hint != null) setHint(hint)
            if (multiLine) minLines = 3 else setSingleLine(true)
        }
        val pad = (20 * activity.resources.displayMetrics.density).toInt()
        val container = FrameLayout(activity).apply {
            setPadding(pad, pad / 2, pad, 0)
            addView(et)
        }
        AlertDialog.Builder(activity)
            .setTitle(title)
            .setView(container)
            .setPositiveButton(positive) { _, _ -> onOk(et.text.toString().trim()) }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    fun rename(activity: Activity, item: TileBackupItem, onDone: () -> Unit) {
        inputDialog(activity, R.string.action_rename, item.name) { name ->
            if (name.isNotEmpty()) {
                TileBackupStore.renameBackup(activity, item.id, name)
                item.name = name
                onDone()
            }
        }
    }

    fun confirmDelete(activity: Activity, item: TileBackupItem, onDone: () -> Unit) {
        AlertDialog.Builder(activity)
            .setMessage(activity.getString(R.string.dialog_delete_snapshot, item.name))
            .setPositiveButton(R.string.action_delete) { _, _ ->
                TileBackupStore.deleteBackup(activity, item.id)
                Toast.makeText(activity, R.string.toast_snapshot_deleted, Toast.LENGTH_SHORT).show()
                onDone()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
}
