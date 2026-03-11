package com.tutu.meowhub.core.model

import androidx.annotation.StringRes
import com.tutu.meowhub.R
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

data class TutuCommandSpec(
    @param:StringRes val categoryResId: Int,
    @param:StringRes val nameResId: Int,
    val type: String,
    val params: List<ParamSpec> = emptyList(),
    val hasResponse: Boolean = true,
    val needsDelay: Boolean = false
)

data class ParamSpec(
    val key: String,
    @param:StringRes val labelResId: Int,
    val paramType: ParamType = ParamType.STRING,
    val required: Boolean = false,
    val defaultValue: String = ""
)

enum class ParamType {
    STRING, INT, FLOAT, DOUBLE, BOOLEAN, LONG
}

object TutuCommands {

    fun allCategories(): List<CommandCategory> = listOf(
        inputControl(),
        systemCommands(),
        screenshotAndVideo(),
        uiAutomation(),
        shellCommands(),
        appManagement(),
        communicationAndMedia(),
        deviceInfo(),
        serviceControl()
    )

    data class CommandCategory(
        @param:StringRes val nameResId: Int,
        val commands: List<TutuCommandSpec>
    )

    // region 1. Input Control
    private fun inputControl() = CommandCategory(R.string.cat_input_control, listOf(
        TutuCommandSpec(R.string.subcat_input_control, R.string.cmd_touch, "touch", listOf(
            ParamSpec("action", R.string.param_touch_action, ParamType.INT, true, "0"),
            ParamSpec("x", R.string.param_x, ParamType.INT, true, "500"),
            ParamSpec("y", R.string.param_y, ParamType.INT, true, "800"),
            ParamSpec("screenWidth", R.string.param_screen_width, ParamType.INT, false, "1080"),
            ParamSpec("screenHeight", R.string.param_screen_height, ParamType.INT, false, "1920"),
        ), hasResponse = false, needsDelay = true),
        TutuCommandSpec(R.string.subcat_input_control, R.string.cmd_key, "key", listOf(
            ParamSpec("action", R.string.param_key_action, ParamType.INT, true, "0"),
            ParamSpec("keycode", R.string.param_keycode, ParamType.INT, true, "4"),
        ), hasResponse = false, needsDelay = true),
        TutuCommandSpec(R.string.subcat_input_control, R.string.cmd_text, "text", listOf(
            ParamSpec("text", R.string.param_text_content, ParamType.STRING, true, "Hello"),
        ), hasResponse = false, needsDelay = true),
        TutuCommandSpec(R.string.subcat_input_control, R.string.cmd_scroll, "scroll", listOf(
            ParamSpec("x", R.string.param_x, ParamType.INT, true, "540"),
            ParamSpec("y", R.string.param_y, ParamType.INT, true, "960"),
            ParamSpec("hScroll", R.string.param_h_scroll, ParamType.FLOAT, false, "0"),
            ParamSpec("vScroll", R.string.param_v_scroll, ParamType.FLOAT, false, "-3.0"),
            ParamSpec("screenWidth", R.string.param_screen_width, ParamType.INT, false, "1080"),
            ParamSpec("screenHeight", R.string.param_screen_height, ParamType.INT, false, "1920"),
        ), hasResponse = false, needsDelay = true),
        TutuCommandSpec(R.string.subcat_input_control, R.string.cmd_command, "command", listOf(
            ParamSpec("cmd", R.string.param_cmd, ParamType.STRING, true, "HOME"),
        ), hasResponse = false, needsDelay = true),
        TutuCommandSpec(R.string.subcat_input_control, R.string.cmd_start_app, "start_app", listOf(
            ParamSpec("package", R.string.param_package, ParamType.STRING, true, "com.android.settings"),
        ), hasResponse = false),
    ))
    // endregion

    // region 2. System Commands
    private fun systemCommands() = CommandCategory(R.string.cat_system_commands, listOf(
        TutuCommandSpec(R.string.subcat_system_commands, R.string.cmd_set_clipboard, "set_clipboard", listOf(
            ParamSpec("text", R.string.param_clipboard_text, ParamType.STRING, true, "test clipboard"),
            ParamSpec("paste", R.string.param_paste, ParamType.BOOLEAN, false, "false"),
        ), hasResponse = false),
        TutuCommandSpec(R.string.subcat_system_commands, R.string.cmd_get_clipboard, "get_clipboard", hasResponse = false),
        TutuCommandSpec(R.string.subcat_system_commands, R.string.cmd_set_display_power, "set_display_power", listOf(
            ParamSpec("on", R.string.param_display_on, ParamType.BOOLEAN, true, "true"),
        ), hasResponse = false),
    ))
    // endregion

    // region 3. Screenshot & Video
    private fun screenshotAndVideo() = CommandCategory(R.string.cat_screenshot_video, listOf(
        TutuCommandSpec(R.string.subcat_screenshot_video, R.string.cmd_screenshot, "screenshot", listOf(
            ParamSpec("displayId", R.string.param_display_id, ParamType.INT, false, "0"),
            ParamSpec("maxSize", R.string.param_max_size, ParamType.INT, false, "1080"),
            ParamSpec("quality", R.string.param_quality, ParamType.INT, false, "80"),
        )),
        TutuCommandSpec(R.string.subcat_screenshot_video, R.string.cmd_start_video, "start_video", listOf(
            ParamSpec("bitRate", R.string.param_bit_rate, ParamType.INT, false, "500000"),
            ParamSpec("maxFps", R.string.param_max_fps, ParamType.INT, false, "30"),
        )),
        TutuCommandSpec(R.string.subcat_screenshot_video, R.string.cmd_stop_video, "stop_video"),
    ))
    // endregion

    // region 4. UI Automation
    private fun uiAutomation() = CommandCategory(R.string.cat_ui_automation, listOf(
        TutuCommandSpec(R.string.subcat_ui_automation, R.string.cmd_click_by_text, "click_by_text", listOf(
            ParamSpec("text", R.string.param_text_match, ParamType.STRING, true, "OK"),
            ParamSpec("index", R.string.param_index, ParamType.INT, false, "0"),
        ), needsDelay = true),
        TutuCommandSpec(R.string.subcat_ui_automation, R.string.cmd_click_by_id, "click_by_id", listOf(
            ParamSpec("id", R.string.param_resource_id, ParamType.STRING, true, "com.example:id/btn"),
            ParamSpec("index", R.string.param_index, ParamType.INT, false, "0"),
        ), needsDelay = true),
        TutuCommandSpec(R.string.subcat_ui_automation, R.string.cmd_find_element, "find_element", listOf(
            ParamSpec("text", R.string.param_find_text, ParamType.STRING, false, ""),
            ParamSpec("id", R.string.param_find_id, ParamType.STRING, false, ""),
            ParamSpec("className", R.string.param_find_class, ParamType.STRING, false, ""),
        ), needsDelay = true),
        TutuCommandSpec(R.string.subcat_ui_automation, R.string.cmd_long_click, "long_click", listOf(
            ParamSpec("x", R.string.param_x, ParamType.INT, true, "500"),
            ParamSpec("y", R.string.param_y, ParamType.INT, true, "800"),
            ParamSpec("durationMs", R.string.param_duration_ms, ParamType.INT, false, "800"),
        ), needsDelay = true),
        TutuCommandSpec(R.string.subcat_ui_automation, R.string.cmd_swipe, "swipe", listOf(
            ParamSpec("x1", R.string.param_start_x, ParamType.INT, true, "500"),
            ParamSpec("y1", R.string.param_start_y, ParamType.INT, true, "1500"),
            ParamSpec("x2", R.string.param_end_x, ParamType.INT, true, "500"),
            ParamSpec("y2", R.string.param_end_y, ParamType.INT, true, "500"),
            ParamSpec("durationMs", R.string.param_swipe_duration, ParamType.INT, false, "300"),
        ), needsDelay = true),
        TutuCommandSpec(R.string.subcat_ui_automation, R.string.cmd_input_keyevent, "input_keyevent", listOf(
            ParamSpec("keycode", R.string.param_keycode, ParamType.STRING, true, "KEYCODE_ENTER"),
        ), needsDelay = true),
    ))
    // endregion

    // region 5. Shell Commands
    private fun shellCommands() = CommandCategory(R.string.cat_shell_file, listOf(
        TutuCommandSpec(R.string.subcat_shell_file, R.string.cmd_execute_shell, "execute_shell", listOf(
            ParamSpec("command", R.string.param_shell_command, ParamType.STRING, true, "ls /sdcard"),
            ParamSpec("timeout", R.string.param_timeout, ParamType.INT, false, "30"),
        )),
    ))
    // endregion

    // region 6. App Management
    private fun appManagement() = CommandCategory(R.string.cat_app_management, listOf(
        TutuCommandSpec(R.string.subcat_app_management, R.string.cmd_list_packages, "list_packages", listOf(
            ParamSpec("thirdPartyOnly", R.string.param_third_party_only, ParamType.BOOLEAN, false, "true"),
            ParamSpec("includeVersions", R.string.param_include_versions, ParamType.BOOLEAN, false, "true"),
        )),
        TutuCommandSpec(R.string.subcat_app_management, R.string.cmd_get_app_info, "get_app_info", listOf(
            ParamSpec("package", R.string.param_package, ParamType.STRING, true, "com.android.settings"),
        )),
        TutuCommandSpec(R.string.subcat_app_management, R.string.cmd_force_stop_app, "force_stop_app", listOf(
            ParamSpec("package", R.string.param_package, ParamType.STRING, true, "com.example.app"),
        )),
        TutuCommandSpec(R.string.subcat_app_management, R.string.cmd_uninstall_app, "uninstall_app", listOf(
            ParamSpec("package", R.string.param_package, ParamType.STRING, true, "com.example.app"),
            ParamSpec("keepData", R.string.param_keep_data, ParamType.BOOLEAN, false, "false"),
        )),
        TutuCommandSpec(R.string.subcat_app_management, R.string.cmd_install_apk, "install_apk", listOf(
            ParamSpec("path", R.string.param_apk_path, ParamType.STRING, true, "/sdcard/app.apk"),
        )),
        TutuCommandSpec(R.string.subcat_app_management, R.string.cmd_clear_app_data, "clear_app_data", listOf(
            ParamSpec("package", R.string.param_package, ParamType.STRING, true, "com.example.app"),
        )),
    ))
    // endregion

    // region 6. Communication — SMS & Calls
    private fun communicationAndMedia() = CommandCategory(R.string.cat_communication_media, listOf(
        TutuCommandSpec(R.string.subcat_communication_media, R.string.cmd_send_sms, "send_sms", listOf(
            ParamSpec("destination", R.string.param_destination, ParamType.STRING, true, "13800138000"),
            ParamSpec("text", R.string.param_sms_text, ParamType.STRING, true, "Hello"),
        )),
        TutuCommandSpec(R.string.subcat_communication_media, R.string.cmd_read_sms, "read_sms", listOf(
            ParamSpec("limit", R.string.param_limit, ParamType.INT, false, "20"),
            ParamSpec("unreadOnly", R.string.param_unread_only, ParamType.BOOLEAN, false, "false"),
        )),
        TutuCommandSpec(R.string.subcat_communication_media, R.string.cmd_make_call, "make_call", listOf(
            ParamSpec("number", R.string.param_phone_number, ParamType.STRING, true, "13800138000"),
        )),
        TutuCommandSpec(R.string.subcat_communication_media, R.string.cmd_end_call, "end_call"),
    ))
    // endregion

    // region 7. Device Info
    private fun deviceInfo() = CommandCategory(R.string.cat_system_info, listOf(
        TutuCommandSpec(R.string.subcat_system_info, R.string.cmd_get_server_info, "get_server_info"),
        TutuCommandSpec(R.string.subcat_system_info, R.string.cmd_get_device_info, "get_device_info", hasResponse = false),
    ))
    // endregion

    // region 8. Service Control
    private fun serviceControl() = CommandCategory(R.string.cat_service_control, listOf(
        TutuCommandSpec(R.string.subcat_service_control, R.string.cmd_get_ui_nodes, "get_ui_nodes", listOf(
            ParamSpec("mode", R.string.param_ui_nodes_mode, ParamType.INT, false, "2"),
        ), hasResponse = false),
        TutuCommandSpec(R.string.subcat_service_control, R.string.cmd_shutdown, "shutdown"),
    ))
    // endregion

    fun buildJsonForCommand(
        spec: TutuCommandSpec,
        paramValues: Map<String, String>,
        reqId: String? = null
    ): JsonObject = buildJsonObject {
        put("type", spec.type)
        if (reqId != null) {
            put("reqId", reqId)
        }
        for (param in spec.params) {
            val value = paramValues[param.key]
            if (value.isNullOrBlank()) continue
            when (param.paramType) {
                ParamType.INT -> value.toIntOrNull()?.let { put(param.key, it) }
                ParamType.LONG -> value.toLongOrNull()?.let { put(param.key, it) }
                ParamType.FLOAT -> value.toFloatOrNull()?.let { put(param.key, it) }
                ParamType.DOUBLE -> value.toDoubleOrNull()?.let { put(param.key, it) }
                ParamType.BOOLEAN -> value.toBooleanStrictOrNull()?.let { put(param.key, it) }
                ParamType.STRING -> put(param.key, value)
            }
        }
    }
}
