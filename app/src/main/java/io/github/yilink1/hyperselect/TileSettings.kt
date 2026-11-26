package io.github.yilink1.hyperselect

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.Log

internal object TileSettings {
    const val READ = "hyperselect.readQsTiles"
    const val ADD = "hyperselect.addQsTile"
    const val REMOVE = "hyperselect.removeQsTile"
    const val RESTORE = "hyperselect.restoreQsTiles"
    const val TILES = "tiles"
    const val WRITTEN = "written"
    const val MISSING_LAYOUT = "missing_layout"

    private val settingsUri = Uri.parse("content://${Settings.AUTHORITY}")

    enum class UpdateResult { SUCCESS, LAYOUT_UNAVAILABLE, MISSING_LAYOUT, WRITE_FAILED }

    fun read(context: Context): String? {
        val response = request(context, READ, null)
        if (response?.getBoolean(MISSING_LAYOUT) == true) return null
        return response?.getString(TILES)?.also { RuleStore.saveQsTiles(context, it) }
    }

    fun update(context: Context, method: String, value: String): UpdateResult {
        val response = request(context, method, value)
        return when {
            response?.getBoolean(MISSING_LAYOUT) == true -> UpdateResult.MISSING_LAYOUT
            response?.containsKey(WRITTEN) != true -> UpdateResult.LAYOUT_UNAVAILABLE
            !response.getBoolean(WRITTEN) -> UpdateResult.WRITE_FAILED
            else -> UpdateResult.SUCCESS
        }
    }

    fun hasTiles(tiles: String): Boolean =
        tiles.split(',').any { it.isNotBlank() && it.trim() != "edit" }

    private fun request(context: Context, method: String, value: String?): Bundle? = runCatching {
        context.contentResolver.call(settingsUri, method, value, null)
    }.onFailure {
        Log.w("HyperSelect", "Quick settings request $method failed: ${it.javaClass.simpleName}")
    }.getOrNull()
}
