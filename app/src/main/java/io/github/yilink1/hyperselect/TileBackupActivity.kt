package io.github.yilink1.hyperselect

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.provider.Settings
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class TileBackupActivity : AppCompatActivity() {

    private lateinit var adapter: TileBackupAdapter
    private var currentTiles: String = ""
    private var currentLabels: List<String> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tile_backup)

        findViewById<ImageView>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<TextView>(R.id.btnImport).setOnClickListener { showImportDialog() }

        loadCurrentLayout()

        val backups = TileBackupStore.loadBackups(this)
        val rv = findViewById<RecyclerView>(R.id.rvBackups)
        rv.layoutManager = LinearLayoutManager(this)

        adapter = TileBackupAdapter(
            currentTiles = currentTiles,
            currentLabels = currentLabels,
            backups = backups,
            onSaveCurrent = { showSaveDialog() },
            onApply = { item -> applyLayout(item) },
            onCopy = { item -> copyLayout(item) },
            onRename = { item -> showRenameDialog(item) },
            onDelete = { item -> deleteSnapshot(item) }
        )
        rv.adapter = adapter
    }

    private fun loadCurrentLayout() {
        currentTiles = runCatching {
            Settings.Secure.getString(contentResolver, ConfigProvider.SETTING_QS_TILES)
        }.getOrNull()?.takeIf { it.isNotBlank() } ?: RuleStore.loadQsTiles(this)

        currentLabels = TileBackupStore.parsePreviewLabels(this, currentTiles)
    }

    private fun showSaveDialog() {
        val defaultName = "布局快照 " + SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date())
        val et = EditText(this).apply {
            setText(defaultName)
            setSelection(defaultName.length)
            setSingleLine(true)
        }
        val container = FrameLayout(this).apply {
            val pad = (20 * resources.displayMetrics.density).toInt()
            setPadding(pad, (10 * resources.displayMetrics.density).toInt(), pad, 0)
            addView(et)
        }

        AlertDialog.Builder(this)
            .setTitle(R.string.dialog_save_snapshot_title)
            .setView(container)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val name = et.text.toString().trim().ifEmpty { defaultName }
                val item = TileBackupItem(
                    name = name,
                    tiles = currentTiles,
                    count = currentLabels.size,
                    previewLabels = currentLabels
                )
                TileBackupStore.addBackup(this, item)
                adapter.updateBackups(TileBackupStore.loadBackups(this))
                Toast.makeText(this, R.string.toast_snapshot_saved, Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun applyLayout(item: TileBackupItem) {
        // 1. 本地更新
        RuleStore.saveQsTiles(this, item.tiles)

        // 2. 写入系统配置
        val success = runCatching {
            Settings.Secure.putString(contentResolver, ConfigProvider.SETTING_QS_TILES, item.tiles)
        }.getOrDefault(false)

        if (success) {
            Toast.makeText(this, R.string.toast_layout_applied, Toast.LENGTH_SHORT).show()
            loadCurrentLayout()
            adapter.updateCurrent(currentTiles, currentLabels)
        } else {
            Toast.makeText(this, R.string.tile_update_failed, Toast.LENGTH_SHORT).show()
        }
    }

    private fun copyLayout(item: TileBackupItem) {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("sysui_qs_tiles", item.tiles)
        cm.setPrimaryClip(clip)
        Toast.makeText(this, R.string.toast_copied_to_clipboard, Toast.LENGTH_SHORT).show()
    }

    private fun showRenameDialog(item: TileBackupItem) {
        val et = EditText(this).apply {
            setText(item.name)
            setSelection(item.name.length)
            setSingleLine(true)
        }
        val container = FrameLayout(this).apply {
            val pad = (20 * resources.displayMetrics.density).toInt()
            setPadding(pad, (10 * resources.displayMetrics.density).toInt(), pad, 0)
            addView(et)
        }

        AlertDialog.Builder(this)
            .setTitle(R.string.action_rename)
            .setView(container)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val newName = et.text.toString().trim()
                if (newName.isNotEmpty()) {
                    TileBackupStore.renameBackup(this, item.id, newName)
                    adapter.updateBackups(TileBackupStore.loadBackups(this))
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun deleteSnapshot(item: TileBackupItem) {
        TileBackupStore.deleteBackup(this, item.id)
        adapter.updateBackups(TileBackupStore.loadBackups(this))
        Toast.makeText(this, R.string.toast_snapshot_deleted, Toast.LENGTH_SHORT).show()
    }

    private fun showImportDialog() {
        val et = EditText(this).apply {
            setHint(R.string.dialog_import_snapshot_hint)
            minLines = 3
        }
        val container = FrameLayout(this).apply {
            val pad = (20 * resources.displayMetrics.density).toInt()
            setPadding(pad, (10 * resources.displayMetrics.density).toInt(), pad, 0)
            addView(et)
        }

        AlertDialog.Builder(this)
            .setTitle(R.string.dialog_import_snapshot_title)
            .setView(container)
            .setPositiveButton(R.string.action_import) { _, _ ->
                val input = et.text.toString().trim()
                if (input.isEmpty()) {
                    Toast.makeText(this, R.string.toast_import_empty, Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val previewLabels = TileBackupStore.parsePreviewLabels(this, input)
                val defaultName = "导入快照 " + SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date())
                val item = TileBackupItem(
                    name = defaultName,
                    tiles = input,
                    count = previewLabels.size,
                    previewLabels = previewLabels
                )
                TileBackupStore.addBackup(this, item)
                adapter.updateBackups(TileBackupStore.loadBackups(this))
                Toast.makeText(this, R.string.toast_snapshot_saved, Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
}
