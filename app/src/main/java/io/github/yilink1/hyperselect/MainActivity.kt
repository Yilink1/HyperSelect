package io.github.yilink1.hyperselect

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class MainActivity : AppCompatActivity() {

    private lateinit var dotStatus: View
    private lateinit var tvStatusTitle: TextView
    private lateinit var tvTextActionBadge: TextView
    private lateinit var tvShareActionBadge: TextView
    private lateinit var tvOpenWithActionBadge: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content)) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(0, systemBars.top, 0, systemBars.bottom)
            insets
        }

        dotStatus = findViewById(R.id.dotStatus)
        tvStatusTitle = findViewById(R.id.tvStatusTitle)
        tvTextActionBadge = findViewById(R.id.tvTextActionBadge)
        tvShareActionBadge = findViewById(R.id.tvShareActionBadge)
        tvOpenWithActionBadge = findViewById(R.id.tvOpenWithActionBadge)

        findViewById<View>(R.id.cardTextAction).setOnClickListener {
            startActivity(Intent(this, TextActionActivity::class.java))
        }
        findViewById<View>(R.id.cardShareAction).setOnClickListener {
            startActivity(Intent(this, ShareActionActivity::class.java))
        }
        findViewById<View>(R.id.cardOpenWithAction).setOnClickListener {
            startActivity(Intent(this, OpenWithActionActivity::class.java))
        }
        findViewById<View>(R.id.cardModuleStatus).setOnClickListener {
            startActivity(Intent(this, IntroActivity::class.java))
        }
        findViewById<View>(R.id.btnMenu).setOnClickListener { anchor ->
            showPopupMenu(
                anchor,
                listOf(
                    Pair("Feature Demo", { startActivity(Intent(this, IntroActivity::class.java)) }),
                    Pair("About HyperSelect", { startActivity(Intent(this, AboutActivity::class.java)) })
                )
            )
        }

        // Show tutorial on first install
        val sp = getSharedPreferences("hyper_select_prefs", MODE_PRIVATE)
        if (!sp.getBoolean("intro_shown", false)) {
            startActivity(Intent(this, IntroActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        updateStatusAndCounts()
    }

    private fun updateStatusAndCounts() {
        if (RuleStore.isModuleActiveSinceBoot(this)) {
            dotStatus.setBackgroundResource(R.drawable.bg_status_circle_ok)
            tvStatusTitle.text = "Module is active"
        } else {
            dotStatus.setBackgroundResource(R.drawable.bg_status_circle_fail)
            tvStatusTitle.text = "Module not active · Enable in LSPosed and reboot"
        }

        val config = RuleStore.load(this)
        tvTextActionBadge.text = hiddenLabel(config.blacklist.size)
        tvShareActionBadge.text = hiddenLabel(config.shareBlacklist.size)
        tvOpenWithActionBadge.text = hiddenLabel(config.openWithBlacklist.size)
    }

    private fun hiddenLabel(count: Int): String = if (count > 0) "Hidden $count" else "—"
}
