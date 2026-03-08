// 禁止使用 Cursor/Red Hat 扩展的 JRE 构建（该 JRE 无 jlink，会导致打包失败）
if (System.getProperty("java.home").contains(".cursor")) {
    throw GradleException(
        "当前使用的是 Cursor 内置 JRE，无法完成 Android 构建。\n" +
        "请用 Android Studio 打开项目并编译，或在终端执行：\n" +
        "  export JAVA_HOME=/Users/ziv/Library/Java/JavaVirtualMachines/corretto-21.0.6/Contents/Home\n" +
        "  ./gradlew --stop\n" +
        "  ./gradlew assembleDebug"
    )
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
}