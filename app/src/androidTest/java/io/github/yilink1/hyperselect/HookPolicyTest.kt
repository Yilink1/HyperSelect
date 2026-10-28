package io.github.yilink1.hyperselect

import android.content.Intent
import android.content.pm.PackageInfo
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HookPolicyTest {
    @Test fun invalidResponsesAreNotEmptyConfigurations() {
        listOf(null, "", "{", "{}", "null", "{\"blacklist\":[],\"order\":[1]}",
            "{\"blacklist\":null,\"order\":[]}").forEach {
            assertNull("Invalid response: $it", RuleConfig.parseResponse(it))
        }
        assertEquals(RuleConfig(), RuleConfig.parseResponse(RuleConfig().toJson()))
        val rules = RuleConfig(setOf("example/.Action"), listOf("example/.Action"))
        assertEquals(rules, RuleConfig.parseResponse(rules.toJson()))
    }

    @Test fun onlyEditorQsbSearchIsBlocked() {
        fun matches(uri: String) = HookPolicy.isEditorSearch(Intent(Intent.ACTION_VIEW, Uri.parse(uri)))
        assertTrue(matches("qsb://query?word=test&ref=miuiEditor"))
        assertTrue(matches("qsb://query?ref=miui%45ditor"))
        listOf("qsb://query?word=test", "qsb://query?ref=other", "qsb://queryOther?ref=miuiEditor",
            "https://example.com/?ref=miuiEditor", "qsb:query?ref=miuiEditor",
            "qsb://query?text=ref%3DmiuiEditor").forEach { assertFalse(it, matches(it)) }
        assertFalse(HookPolicy.isEditorSearch(null))
        assertFalse(HookPolicy.isEditorSearch(Intent()))
    }

    @Test fun spoofedVersionDoesNotMutateOriginalPackageInfo() {
        val original = PackageInfo().apply {
            packageName = "com.miui.notes"
            longVersionCode = (2L shl 32) + 123L
            versionName = "original"
        }
        val copy = requireNotNull(HookPolicy.copyPackageInfo(original))
        assertNotSame(original, copy)
        copy.longVersionCode = 1L
        assertEquals((2L shl 32) + 123L, original.longVersionCode)
        assertEquals(1L, copy.longVersionCode)
        assertEquals(original.packageName, copy.packageName)
        assertEquals(original.versionName, copy.versionName)
    }
}
