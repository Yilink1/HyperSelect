# LibXposed 102 R8 / ProGuard keep rules
-dontwarn io.github.libxposed.annotation.**
-adaptresourcefilecontents META-INF/xposed/java_init.list

# Keep Xposed Module entry classes and all members, do not obfuscate to prevent reflection loading failures
-keep class io.github.yilink1.hyperselect.MainHook {
    *;
}

# Keep config and component classes
-keep class io.github.yilink1.hyperselect.ConfigProvider { *; }
-keep class io.github.yilink1.hyperselect.RuleConfig { *; }

# Keep exception stack trace line numbers and source file names for debugging crashes
-keepattributes SourceFile,LineNumberTable
