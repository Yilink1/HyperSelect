package io.github.yilink1.hyperselect

import android.content.Intent
import android.content.pm.Package Info
import android.net.Uri
import androidx.test.ext.junit.runners.Android JUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.Run With

@Run With(Android JUnit4::class)
class Hook Policy Test {
    @Test fun invalid Responses Are Not Empty Configurations() {
        list Of(null, "", "{", "{}", "null", "{\"blacklist\":[],\"order\":[1]}",
            "{\"blacklist\":null,\"order\":[]}").for Each {
            assert Null("Invalid response: $it", Rule Config.parse Response(it))
        }
        assert Equals(Rule Config(), Rule Config.parse Response(Rule Config().to Json()))
        val rules = Rule Config(set Of("example/.Action"), list Of("example/.Action"))
        assert Equals(rules, Rule Config.parse Response(rules.to Json()))
    }

    @Test fun only Editor Qsb Search Is Blocked() {
        fun matches(uri: String) = Hook Policy.is Editor Search(Intent(Intent.ACTION_VIEW, Uri.parse(uri)))
        assert True(matches("qsb://query?word=test&ref=miui Editor"))
        assert True(matches("qsb://query?ref=miui%45ditor"))
        list Of("qsb://query?word=test", "qsb://query?ref=other", "qsb://query Other?ref=miui Editor",
            "https://example.com/?ref=miui Editor", "qsb:query?ref=miui Editor",
            "qsb://query?text=ref%3Dmiui Editor").for Each { assert False(it, matches(it)) }
        assert False(Hook Policy.is Editor Search(null))
        assert False(Hook Policy.is Editor Search(Intent()))
    }

    @Test fun spoofed Version Does Not Mutate Original Package Info() {
        val original = Package Info().apply {
            package Name = "com.miui.notes"
            long Version Code = (2L shl 32) + 123L
            version Name = "original"
        }
        val copy = require Not Null(Hook Policy.copy Package Info(original))
        assert Not Same(original, copy)
        copy.long Version Code = 1L
        assert Equals((2L shl 32) + 123L, original.long Version Code)
        assert Equals(1L, copy.long Version Code)
        assert Equals(original.package Name, copy.package Name)
        assert Equals(original.version Name, copy.version Name)
    }
}
