package io.github.yilink1.hyperselect

import android.content.Intent
import android.content.pm.Package Manager
import android.content.pm.Resolve Info
import android.view.View
import android.view.View Group

/** Text Selection：Action items shown when long-pressing text。 */
class Text Action Activity : Manage List Activity() {

    override val screen Title = "Text Selection"
    override val empty Text = "No apps support text actions"
    override val sort By Label When Noorder = false

    private var switch Block Ai Writing: Hyper Switch? = null
    private var row Block Ai Writing: View? = null

    @Suppress("DEPRECATION")
    override fun query Targets(pm: Package Manager): List<Resolve Info> {
        val intent = Intent(Intent.ACTION_PROCESS_TEXT).set Type("text/plain")
        intent.put Extra("io.github.yilink1.hyperselect.EXTRA_RAW_QUERY", true)
        return pm.query Intent Activities(intent, Package Manager.MATCH_DEFAULT_ONLY)
    }

    override fun blacklist Of(config: Rule Config): Set<String> = config.blacklist
    override fun order Of(config: Rule Config): List<String> = config.customorder
    override fun with Rules(config: Rule Config, blacklist: Set<String>, order: List<String>): Rule Config =
        config.copy(blacklist = blacklist, customorder = order)

    override fun on Create Extra(container: View Group) {
        val row = layout Inflater.inflate(R.layout.item_setting_switch, container, false)
        container.add View(row)
        container.visibility = View.VISIBLE

        val switch View = row.find View By Id<Hyper Switch>(R.id.switch Block Ai Writing)
        switch Block Ai Writing = switch View
        row Block Ai Writing = row

        // Cannot operate until config is loaded to avoid overwriting with defaults
        row.is Enabled = false
        switch View.is Enabled = false

        row.set On Click Listener { switch View.toggle() }
        switch View.on Toggled = { save Rules() }
    }

    override fun on Config Loaded(config: Rule Config) {
        val switch View = switch Block Ai Writing ?: return
        switch View.set Checked(config.block Ai Writing, animate = false)
        switch View.is Enabled = true
        row Block Ai Writing?.is Enabled = true
    }

    override fun apply Extra(config: Rule Config): Rule Config =
        config.copy(block Ai Writing = switch Block Ai Writing?.checked ?: config.block Ai Writing)
}
