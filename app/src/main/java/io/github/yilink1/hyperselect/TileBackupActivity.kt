package io.github.yilink1.hyperselect

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class TileBackupActivity : AppCompatActivity() {

    private lateinit var adapter: TileBackupAdapter
    private var currentTiles: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tile_backup)

        findViewById<ImageView>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<ImageView>(R.id.btnAdd).setOnClickListener { showSaveDialog() }
        findViewById<ImageView>(R.id.btnMore).setOnClickListener { v ->
            PopupMenu(this, v).apply {
                menu.add(0, 1, 0, R.string.action_import)
                setOnMenuItemClickListener { showImportDialog(); true }
            }.show()
        }

        currentTiles = TileBackupActions.readCurrentTiles(this)
        adapter = TileBackupAdapter(
            currentTiles = currentTiles,
            backups = TileBackupStore.loadBackups(this),
            onOpenCurrent = { openDetail(null) },
            onOpen = { openDetail(it.id) },
            onLongPress = { item, v -> showItemMenu(item, v) }
        )
        findViewById<RecyclerView>(R.id.rvBackups).apply {
            layoutManager = LinearLayoutManager(this@TileBackupActivity)
            adapter = this@TileBackupActivity.adapter
        }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        currentTiles = TileBackupActions.readCurrentTiles(this)
        adapter.update(currentTiles, TileBackupStore.loadBackups(this))
    }

    private fun openDetail(id: String?) {
        startActivity(Intent(this, TileBackupDetailActivity::class.java).apply {
            if (id != null) putExtra(TileBackupDetailActivity.EXTRA_ID, id)
        })
    }

    private fun showItemMenu(item: TileBackupItem, anchor: View) {
        PopupMenu(this, anchor).apply {
            menu.add(0, 1, 0, R.string.action_rename)
            menu.add(0, 2, 1, R.string.action_copy)
            menu.add(0, 3, 2, R.string.action_delete)
            setOnMenuItemClickListener {
                when (it.itemId) {
                    1 -> TileBackupActions.rename(this@TileBackupActivity, item) { refresh() }
                    2 -> TileBackupActions.copy(this@TileBackupActivity, item)
                    3 -> TileBackupActions.confirmDelete(this@TileBackupActivity, item) { refresh() }
                }
                true
            }
        }.show()
    }

    private fun defaultName(prefix: String) =
        prefix + SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date())

    private fun showSaveDialog() {
        val fallback = defaultName("布局快照 ")
        TileBackupActions.inputDialog(this, R.string.dialog_save_snapshot_title, fallback) { input ->
            saveSnapshot(input.ifEmpty { fallback }, currentTiles)
        }
    }

    private fun showImportDialog() {
        TileBackupActions.inputDialog(
            this, R.string.dialog_import_snapshot_title, "",
            hint = R.string.dialog_import_snapshot_hint, multiLine = true, positive = R.string.action_import
        ) { input ->
            if (input.isEmpty()) {
                Toast.makeText(this, R.string.toast_import_empty, Toast.LENGTH_SHORT).show()
            } else {
                saveSnapshot(defaultName("导入快照 "), input)
            }
        }
    }

    private fun saveSnapshot(name: String, tiles: String) {
        val labels = TileBackupStore.parsePreviewLabels(this, tiles)
        TileBackupStore.addBackup(this, TileBackupItem(
            name = name, tiles = tiles, count = labels.size, previewLabels = labels
        ))
        refresh()
        Toast.makeText(this, R.string.toast_snapshot_saved, Toast.LENGTH_SHORT).show()
    }
}