package io.github.yilink1.hyperselect

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.PopupMenu
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class TileBackupActivity : AppCompatActivity() {

    private lateinit var adapter: TileBackupAdapter
    private var currentTiles: String? = null
    private var systemTilesAvailable = false


    private val exportFileLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            val backups = TileBackupStore.loadBackups(this)
            val json = TileBackupStore.exportToJson(backups)
            val success = runCatching {
                contentResolver.openOutputStream(uri)?.use { os ->
                    os.write(json.toByteArray(Charsets.UTF_8))
                }
                true
            }.getOrDefault(false)
            if (success) {
                Toast.makeText(this, R.string.toast_export_success, Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, R.string.toast_export_failed, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private val importFileLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val json = runCatching {
                contentResolver.openInputStream(uri)?.use { inputStream ->
                    inputStream.bufferedReader(Charsets.UTF_8).readText()
                }
            }.getOrNull()
            val imported = json?.let { TileBackupStore.importFromJson(it) }
            if (imported.isNullOrEmpty()) {
                Toast.makeText(this, R.string.toast_import_invalid, Toast.LENGTH_SHORT).show()
            } else {
                val count = TileBackupStore.mergeBackups(this, imported)
                if (count > 0) {
                    refresh()
                    Toast.makeText(this, getString(R.string.toast_import_success, count), Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, R.string.toast_import_all_exists, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tile_backup)

        findViewById<ImageView>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<ImageView>(R.id.btnAdd).setOnClickListener { showSaveDialog() }
        findViewById<ImageView>(R.id.btnMore).setOnClickListener { v ->
            PopupMenu(this, v).apply {
                menu.add(0, 1, 0, R.string.action_import)
                menu.add(0, 2, 1, R.string.action_export)
                setOnMenuItemClickListener {
                    when (it.itemId) {
                        1 -> importFileLauncher.launch(arrayOf("application/json", "text/*", "*/*"))
                        2 -> exportAllSnapshots()
                    }
                    true
                }
            }.show()
        }

        currentTiles = TileBackupActions.readCurrentTiles(this)
        systemTilesAvailable = TileBackupActions.readSystemTiles(this) != null
        adapter = TileBackupAdapter(
            currentTiles = currentTiles,
            systemTilesAvailable = systemTilesAvailable,
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
        systemTilesAvailable = TileBackupActions.readSystemTiles(this) != null
        adapter.update(currentTiles, systemTilesAvailable, TileBackupStore.loadBackups(this))
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
        val tiles = TileBackupActions.readSystemTiles(this)
        if (tiles == null) {
            Toast.makeText(this, R.string.tile_layout_unavailable, Toast.LENGTH_SHORT).show()
            return
        }
        val fallback = defaultName("快照 ")
        TileBackupActions.inputDialog(this, R.string.dialog_save_snapshot_title, fallback) { input ->
            saveSnapshot(input.ifEmpty { fallback }, tiles)
        }
    }

    private fun exportAllSnapshots() {
        val backups = TileBackupStore.loadBackups(this)
        if (backups.isEmpty()) {
            Toast.makeText(this, R.string.toast_export_empty, Toast.LENGTH_SHORT).show()
            return
        }
        val dateStr = SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault()).format(Date())
        exportFileLauncher.launch("HyperSelect_Tiles_$dateStr.json")
    }

    private fun saveSnapshot(name: String, tiles: String) {
        TileBackupStore.addBackup(this, TileBackupItem(
            name = name, tiles = tiles
        ))
        refresh()
        Toast.makeText(this, R.string.toast_snapshot_saved, Toast.LENGTH_SHORT).show()
    }
}
