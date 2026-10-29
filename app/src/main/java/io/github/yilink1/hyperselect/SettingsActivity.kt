package io.github.yilink1.hyperselect

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class SettingsActivity : AppCompatActivity() {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase))
    }

    private lateinit var switchModule: HyperSwitch
    private lateinit var switchText: HyperSwitch
    private lateinit var switchShare: HyperSwitch
    private lateinit var switchOpenWith: HyperSwitch
    private lateinit var rowText: View
    private lateinit var rowShare: View
    private lateinit var rowOpenWith: View
    private lateinit var tvDiag: TextView

    private val exportLauncher =
        registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
            if (uri == null) return@registerForActivityResult
            runCatching {
                contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(RuleStore.load(this).toJson().toByteArray(Charsets.UTF_8))
                } ?: error("cannot open file")
            }.onSuccess {
                toast(getString(R.string.export_success))
            }.onFailure {
                toast(getString(R.string.export_failed, it.message ?: "unknown"))
            }
        }

    private val importLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri == null) return@registerForActivityResult
            val bytes = readCapped(uri, MAX_IMPORT_BYTES)
            val parsed = bytes?.let { RuleConfig.parseResponse(it.toString(Charsets.UTF_8)) }
            if (parsed == null) {
                toast(getString(R.string.import_failed))
                return@registerForActivityResult
            }
            RuleStore.save(this, parsed)
            contentResolver.notifyChange(ConfigProvider.CONTENT_URI, null)
            render(parsed)
            toast(getString(R.string.import_success))
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content)) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(0, systemBars.top, 0, systemBars.bottom)
            insets
        }

        val toolbar: Toolbar = findViewById(R.id.toolbarSettings)
        setSupportActionBar(toolbar)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            title = getString(R.string.settings_title)
        }

        switchModule = findViewById(R.id.switchModule)
        switchText = findViewById(R.id.switchText)
        switchShare = findViewById(R.id.switchShare)
        switchOpenWith = findViewById(R.id.switchOpenWith)
        rowText = findViewById(R.id.rowText)
        rowShare = findViewById(R.id.rowShare)
        rowOpenWith = findViewById(R.id.rowOpenWith)
        tvDiag = findViewById(R.id.tvSettingsDiag)

        switchModule.onToggled = { checked -> updateConfig { it.copy(moduleEnabled = checked) } }
        switchText.onToggled = { checked -> updateConfig { it.copy(textEnabled = checked) } }
        switchShare.onToggled = { checked -> updateConfig { it.copy(shareEnabled = checked) } }
        switchOpenWith.onToggled = { checked -> updateConfig { it.copy(openWithEnabled = checked) } }

        findViewById<View>(R.id.rowMaster).setOnClickListener { switchModule.toggle() }
        rowText.setOnClickListener { if (switchText.isEnabled) switchText.toggle() }
        rowShare.setOnClickListener { if (switchShare.isEnabled) switchShare.toggle() }
        rowOpenWith.setOnClickListener { if (switchOpenWith.isEnabled) switchOpenWith.toggle() }

        findViewById<View>(R.id.rowExport).setOnClickListener {
            exportLauncher.launch("hyperselect-rules.json")
        }
        findViewById<View>(R.id.rowImport).setOnClickListener {
            importLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream"))
        }
        findViewById<View>(R.id.rowCheckUpdate).setOnClickListener { checkForUpdates() }
        findViewById<View>(R.id.rowAbout).setOnClickListener {
            startActivity(Intent(this, AboutActivity::class.java))
        }

        findViewById<TextView>(R.id.tvSettingsVersion).text =
            getString(R.string.settings_version, versionName())
    }

    override fun onResume() {
        super.onResume()
        render(RuleStore.load(this))
        refreshDiagnostics()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun updateConfig(transform: (RuleConfig) -> RuleConfig) {
        val updated = transform(RuleStore.load(this))
        RuleStore.save(this, updated)
        contentResolver.notifyChange(ConfigProvider.CONTENT_URI, null)
        render(updated)
    }

    private fun render(config: RuleConfig) {
        switchModule.setChecked(config.moduleEnabled, animate = false)
        switchText.setChecked(config.textEnabled, animate = false)
        switchShare.setChecked(config.shareEnabled, animate = false)
        switchOpenWith.setChecked(config.openWithEnabled, animate = false)

        val subEnabled = config.moduleEnabled
        switchText.isEnabled = subEnabled
        switchShare.isEnabled = subEnabled
        switchOpenWith.isEnabled = subEnabled
        rowText.isEnabled = subEnabled
        rowShare.isEnabled = subEnabled
        rowOpenWith.isEnabled = subEnabled
        rowText.alpha = if (subEnabled) 1f else 0.5f
        rowShare.alpha = if (subEnabled) 1f else 0.5f
        rowOpenWith.alpha = if (subEnabled) 1f else 0.5f
    }

    private fun refreshDiagnostics() {
        tvDiag.text = if (RuleStore.isModuleActiveSinceBoot(this)) {
            getString(R.string.settings_diag_active)
        } else {
            getString(R.string.settings_diag_inactive)
        }
    }

    private fun versionName(): String = runCatching {
        packageManager.getPackageInfo(packageName, 0).versionName ?: "?"
    }.getOrDefault("?")

    // ───────── Update check ─────────
    private fun checkForUpdates() {
        toast(getString(R.string.update_checking))
        Thread {
            val result = runCatching { fetchLatestRelease() }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                result.onSuccess { (tag, url) ->
                    val current = versionName()
                    if (isNewer(tag, current)) {
                        AlertDialog.Builder(this)
                            .setTitle(getString(R.string.update_available, tag))
                            .setMessage(getString(R.string.update_available_desc, current, tag))
                            .setPositiveButton(getString(R.string.update_open)) { _, _ ->
                                runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
                            }
                            .setNegativeButton(getString(R.string.update_cancel), null)
                            .show()
                    } else {
                        toast(getString(R.string.update_latest))
                    }
                }.onFailure {
                    toast(getString(R.string.update_failed, it.message ?: "unknown"))
                }
            }
        }.start()
    }

    private fun fetchLatestRelease(): Pair<String, String> {
        val conn = (URL(RELEASE_API).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8000
            readTimeout = 8000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "HyperSelect/${versionName()}")
        }
        try {
            if (conn.responseCode !in 200..299) error("HTTP ${conn.responseCode}")
            val body = conn.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
            val obj = JSONObject(body)
            val tag = obj.optString("tag_name").trim()
            val url = obj.optString("html_url").trim()
            if (tag.isEmpty() || url.isEmpty()) error("empty response")
            if (!url.startsWith("https://")) error("invalid url")
            return Pair(tag, url)
        } finally {
            conn.disconnect()
        }
    }

    private fun isNewer(latest: String, current: String): Boolean {
        fun parts(s: String): List<Int> = s.trim()
            .removePrefix("v").removePrefix("V")
            .split('.', '-', '+')
            .mapNotNull { it.toIntOrNull() }
        val a = parts(latest)
        val b = parts(current)
        if (a.isEmpty()) return false
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    /** Read a SAF file with a hard size cap so a huge file cannot exhaust memory. */
    private fun readCapped(uri: Uri, maxBytes: Int): ByteArray? = runCatching {
        contentResolver.openInputStream(uri)?.use { input ->
            val out = java.io.ByteArrayOutputStream()
            val buf = ByteArray(8 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                out.write(buf, 0, n)
                if (out.size() > maxBytes) return null
            }
            out.toByteArray()
        }
    }.getOrNull()

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    companion object {
        private const val RELEASE_API = "https://api.github.com/repos/TofuGG/HyperSelect/releases/latest"
        private const val MAX_IMPORT_BYTES = 2 * 1024 * 1024
    }
}
