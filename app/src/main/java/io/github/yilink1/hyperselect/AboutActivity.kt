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
        val defaultVersionText = try {
            val pInfo = packageManager.getPackageInfo(packageName, 0)
            @Suppress("DEPRECATION")
            val code = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                pInfo.longVersionCode
            } else {
                pInfo.versionCode.toLong()
            }
            "v${pInfo.versionName} ($code)"
        } catch (_: Exception) {
            "v1.1.2"
        }
        tvVersion.text = defaultVersionText

        val colorSecondary = androidx.core.content.ContextCompat.getColor(this, R.color.hyper_text_secondary)
        val colorAccent = androidx.core.content.ContextCompat.getColor(this, R.color.hyper_accent)

        var clickCount = 0
        val resetHandler = android.os.Handler(android.os.Looper.getMainLooper())
        val resetRunnable = Runnable {
            clickCount = 0
            tvVersion.text = defaultVersionText
            tvVersion.setTextColor(colorSecondary)
        }

        tvVersion.isHapticFeedbackEnabled = true
        tvVersion.setOnClickListener { view ->
            resetHandler.removeCallbacks(resetRunnable)
            clickCount++

            val config = RuleStore.load(this)
            val isEnabled = config.debugLogging

            if (!isEnabled) {
                if (clickCount < 5) {
                    view.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                    val remaining = 5 - clickCount
                    tvVersion.text = getString(R.string.debug_log_step_enable, remaining)
                    tvVersion.setTextColor(colorSecondary)
                    resetHandler.postDelayed(resetRunnable, 2500L)
                } else {
                    view.performHapticFeedback(android.view.HapticFeedbackConstants.CONFIRM)
                    val updated = config.copy(debugLogging = true)
                    RuleStore.save(this, updated)
                    contentResolver.notifyChange(ConfigProvider.CONTENT_URI, null)
                    tvVersion.text = getString(R.string.debug_log_enabled)
                    tvVersion.setTextColor(colorAccent)
                    clickCount = 0
                    resetHandler.postDelayed(resetRunnable, 2500L)
                }
            } else {
                if (clickCount == 1) {
                    view.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                    tvVersion.text = getString(R.string.debug_log_status_active)
                    tvVersion.setTextColor(colorSecondary)
                    resetHandler.postDelayed(resetRunnable, 2500L)
                } else if (clickCount < 5) {
                    view.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                    val remaining = 5 - clickCount
                    tvVersion.text = getString(R.string.debug_log_step_disable, remaining)
                    tvVersion.setTextColor(colorSecondary)
                    resetHandler.postDelayed(resetRunnable, 2500L)
                } else {
                    view.performHapticFeedback(android.view.HapticFeedbackConstants.CONFIRM)
                    val updated = config.copy(debugLogging = false)
                    RuleStore.save(this, updated)
                    contentResolver.notifyChange(ConfigProvider.CONTENT_URI, null)
                    tvVersion.text = getString(R.string.debug_log_disabled)
                    tvVersion.setTextColor(colorAccent)
                    clickCount = 0
                    resetHandler.postDelayed(resetRunnable, 2500L)
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
