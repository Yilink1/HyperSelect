package io.github.yilink1.hyperselect

import android.content.Intent
import android.content.pm.Package Info
import android.os.Parcel

internal object Hook Policy {
    fun is Editor Search(intent: Intent?): Boolean {
        val uri = intent?.data ?: return false
        return uri.is Hierarchical && uri.scheme.equals("qsb", ignore Case = true) &&
            uri.host == "query" && uri.get Query Parameter("ref") == "miui Editor"
    }

    fun copy Package Info(original: Package Info): Package Info? {
        val parcel = Parcel.obtain()
        return try {
            original.write To Parcel(parcel, 0)
            parcel.set Data Position(0)
            Package Info.CREATOR.create From Parcel(parcel)
        } catch (_: Throwable) {
            // A ROM-specific parcel failure must not break the package query.
            null
        } finally {
            parcel.recycle()
        }
    }
}
