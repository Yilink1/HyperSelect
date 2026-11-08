package io.github.yilink1.hyperselect

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class AboutActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_about)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content)) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(0, systemBars.top, 0, systemBars.bottom)
            insets
        }

        val toolbar: Toolbar = findViewById(R.id.toolbarAbout)
        setSupportActionBar(toolbar)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            title = getString(R.string.title_about)
        }

        val tvVersion: TextView = findViewById(R.id.tvAboutVersion)
        try {
            val pInfo = packageManager.getPackageInfo(packageName, 0)
            @Suppress("DEPRECATION")
            val code = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                pInfo.longVersionCode
            } else {
                pInfo.versionCode.toLong()
            }
            tvVersion.text = "v${pInfo.versionName} ($code)"
        } catch (_: Exception) {
            tvVersion.text = "v1.1.1"
        }

        var clickCount = 0
        var lastClickTime = 0L

        tvVersion.setOnClickListener {
            val now = android.os.SystemClock.uptimeMillis()
            if (now - lastClickTime > 2000L) {
                clickCount = 0
            }
            lastClickTime = now
            clickCount++

            val config = RuleStore.load(this)
            val isEnabled = config.debugLogging

            if (!isEnabled) {
                when (clickCount) {
                    1, 2 -> { /* Silent for first 2 taps */ }
                    3, 4 -> {
                        val remaining = 5 - clickCount
                        showInstantToast(getString(R.string.debug_log_countdown_enable, remaining))
                    }
                    5 -> {
                        val updated = config.copy(debugLogging = true)
                        RuleStore.save(this, updated)
                        contentResolver.notifyChange(ConfigProvider.CONTENT_URI, null)
                        showInstantToast(getString(R.string.debug_log_enabled))
                        clickCount = 0
                    }
                }
            } else {
                when (clickCount) {
                    1 -> {
                        showInstantToast(getString(R.string.debug_log_status_enabled))
                    }
                    2, 3, 4 -> {
                        val remaining = 5 - clickCount
                        showInstantToast(getString(R.string.debug_log_countdown_disable, remaining))
                    }
                    5 -> {
                        val updated = config.copy(debugLogging = false)
                        RuleStore.save(this, updated)
                        contentResolver.notifyChange(ConfigProvider.CONTENT_URI, null)
                        showInstantToast(getString(R.string.debug_log_disabled))
                        clickCount = 0
                    }
                }
            }
        }

        findViewById<View>(R.id.btnAboutHelp).setOnClickListener {
            showHelpDialog()
        }

        findViewById<View>(R.id.btnAboutGithub).setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/yilink1/HyperSelect"))
            try {
                startActivity(intent)
            } catch (_: Exception) {
            }
        }
    }

    private var activeToast: android.widget.Toast? = null

    private fun showInstantToast(message: String) {
        activeToast?.cancel()
        activeToast = android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_SHORT).apply { show() }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun showHelpDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_help, null)
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        dialogView.findViewById<View>(R.id.btnHelpConfirm).setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()

        dialog.window?.let { window ->
            window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            window.setDimAmount(0.45f)
            window.setGravity(Gravity.BOTTOM)
            window.setWindowAnimations(android.R.style.Animation_InputMethod)
            val marginH = (16f * resources.displayMetrics.density).toInt()
            val marginV = (16f * resources.displayMetrics.density).toInt()
            val displayWidth = resources.displayMetrics.widthPixels
            window.setLayout(displayWidth - marginH * 2, ViewGroup.LayoutParams.WRAP_CONTENT)
            val lp = window.attributes
            lp.y = marginV
            window.attributes = lp
        }
    }
}
