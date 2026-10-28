package io.github.yilink1.hyperselect

import android.content.Intent
import android.content.pm.PackageInfo
import android.os.Parcel

internal object HookPolicy {
    fun isEditorSearch(intent: Intent?): Boolean {
        val uri = intent?.data ?: return false
        return uri.isHierarchical && uri.scheme.equals("qsb", ignoreCase = true) &&
            uri.host == "query" && uri.getQueryParameter("ref") == "miuiEditor"
    }

    fun copyPackageInfo(original: PackageInfo): PackageInfo? {
        val parcel = Parcel.obtain()
        return try {
            original.writeToParcel(parcel, 0)
            parcel.setDataPosition(0)
            PackageInfo.CREATOR.createFromParcel(parcel)
        } catch (_: Throwable) {
            // A ROM-specific parcel failure must not break the package query.
            null
        } finally {
            parcel.recycle()
        }
    }
}
