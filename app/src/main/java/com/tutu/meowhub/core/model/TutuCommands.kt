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
        shellAndFile(),
        appManagement(),
        deviceControl(),
        communicationAndMedia(),
        lockAndSecurity(),
        systemInfo(),
        networkAndPeripheral(),
        notificationAndMedia(),
        serviceControl(),
        virtualDisplay()
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

    // region 5. Shell & File
    private fun shellAndFile() = CommandCategory(R.string.cat_shell_file, listOf(
        TutuCommandSpec(R.string.subcat_shell_file, R.string.cmd_execute_shell, "execute_shell", listOf(
            ParamSpec("command", R.string.param_shell_command, ParamType.STRING, true, "ls /sdcard"),
            ParamSpec("timeout", R.string.param_timeout, ParamType.INT, false, "30"),
        )),
        TutuCommandSpec(R.string.subcat_shell_file, R.string.cmd_list_files, "list_files", listOf(
            ParamSpec("path", R.string.param_dir_path, ParamType.STRING, true, "/sdcard"),
        )),
        TutuCommandSpec(R.string.subcat_shell_file, R.string.cmd_read_file, "read_file", listOf(
            ParamSpec("path", R.string.param_file_path, ParamType.STRING, true, "/sdcard/test.txt"),
        )),
        TutuCommandSpec(R.string.subcat_shell_file, R.string.cmd_write_file, "write_file", listOf(
            ParamSpec("path", R.string.param_file_path, ParamType.STRING, true, "/sdcard/test.txt"),
            ParamSpec("content", R.string.param_file_content, ParamType.STRING, true, "hello"),
        )),
        TutuCommandSpec(R.string.subcat_shell_file, R.string.cmd_delete_file, "delete_file", listOf(
            ParamSpec("path", R.string.param_file_path, ParamType.STRING, true, "/sdcard/test.txt"),
        )),
        TutuCommandSpec(R.string.subcat_shell_file, R.string.cmd_analyze_storage, "analyze_storage"),
        TutuCommandSpec(R.string.subcat_shell_file, R.string.cmd_find_large_files, "find_large_files", listOf(
            ParamSpec("path", R.string.param_search_path, ParamType.STRING, false, "/sdcard"),
            ParamSpec("minSizeMB", R.string.param_min_size_mb, ParamType.LONG, false, "10"),
            ParamSpec("limit", R.string.param_limit, ParamType.INT, false, "20"),
        )),
        TutuCommandSpec(R.string.subcat_shell_file, R.string.cmd_download_file, "download_file", listOf(
            ParamSpec("url", R.string.param_download_url, ParamType.STRING, true, "https://example.com/file.zip"),
            ParamSpec("savePath", R.string.param_save_path, ParamType.STRING, false, "/sdcard/file.zip"),
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

    // region 7. Device Control
    private fun deviceControl() = CommandCategory(R.string.cat_device_control, listOf(
        TutuCommandSpec(R.string.subcat_device_control, R.string.cmd_set_brightness, "set_brightness", listOf(
            ParamSpec("value", R.string.param_brightness, ParamType.INT, true, "128"),
            ParamSpec("mode", R.string.param_mode, ParamType.STRING, false, "manual"),
        )),
        TutuCommandSpec(R.string.subcat_device_control, R.string.cmd_set_volume, "set_volume", listOf(
            ParamSpec("streamType", R.string.param_stream_type, ParamType.INT, false, "3"),
            ParamSpec("value", R.string.param_volume_value, ParamType.INT, true, "10"),
        )),
        TutuCommandSpec(R.string.subcat_device_control, R.string.cmd_get_volume, "get_volume"),
        TutuCommandSpec(R.string.subcat_device_control, R.string.cmd_set_rotation, "set_rotation", listOf(
            ParamSpec("rotation", R.string.param_rotation, ParamType.INT, false, "0"),
            ParamSpec("lock", R.string.param_lock_rotation, ParamType.BOOLEAN, false, "true"),
        )),
        TutuCommandSpec(R.string.subcat_device_control, R.string.cmd_open_url, "open_url", listOf(
            ParamSpec("url", R.string.param_url, ParamType.STRING, true, "https://www.baidu.com"),
        )),
        TutuCommandSpec(R.string.subcat_device_control, R.string.cmd_set_wifi, "set_wifi", listOf(
            ParamSpec("enabled", R.string.param_enabled, ParamType.BOOLEAN, true, "true"),
        )),
        TutuCommandSpec(R.string.subcat_device_control, R.string.cmd_set_bluetooth, "set_bluetooth", listOf(
            ParamSpec("enabled", R.string.param_enabled, ParamType.BOOLEAN, true, "true"),
        )),
        TutuCommandSpec(R.string.subcat_device_control, R.string.cmd_read_contacts, "read_contacts", listOf(
            ParamSpec("limit", R.string.param_limit, ParamType.INT, false, "50"),
            ParamSpec("query", R.string.param_query, ParamType.STRING, false, ""),
        )),
        TutuCommandSpec(R.string.subcat_device_control, R.string.cmd_read_call_log, "read_call_log", listOf(
            ParamSpec("limit", R.string.param_limit, ParamType.INT, false, "20"),
        )),
        TutuCommandSpec(R.string.subcat_device_control, R.string.cmd_set_location_mock, "set_location_mock", listOf(
            ParamSpec("latitude", R.string.param_latitude, ParamType.DOUBLE, true, "39.9042"),
            ParamSpec("longitude", R.string.param_longitude, ParamType.DOUBLE, true, "116.4074"),
            ParamSpec("accuracy", R.string.param_accuracy, ParamType.FLOAT, false, "1.0"),
            ParamSpec("altitude", R.string.param_altitude, ParamType.DOUBLE, false, "0.0"),
        )),
        TutuCommandSpec(R.string.subcat_device_control, R.string.cmd_get_setting, "get_setting", listOf(
            ParamSpec("table", R.string.param_table, ParamType.STRING, false, "system"),
            ParamSpec("key", R.string.param_setting_key, ParamType.STRING, true, "screen_brightness"),
        )),
        TutuCommandSpec(R.string.subcat_device_control, R.string.cmd_set_setting, "set_setting", listOf(
            ParamSpec("table", R.string.param_table, ParamType.STRING, false, "system"),
            ParamSpec("key", R.string.param_setting_key, ParamType.STRING, true, "screen_brightness"),
            ParamSpec("value", R.string.param_setting_value, ParamType.STRING, true, "128"),
        )),
    ))
    // endregion

    // region 8. Communication & Media
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
        TutuCommandSpec(R.string.subcat_communication_media, R.string.cmd_get_location, "get_location"),
        TutuCommandSpec(R.string.subcat_communication_media, R.string.cmd_read_notifications, "read_notifications", listOf(
            ParamSpec("limit", R.string.param_limit, ParamType.INT, false, "50"),
        )),
        TutuCommandSpec(R.string.subcat_communication_media, R.string.cmd_speak_tts, "speak_tts", listOf(
            ParamSpec("text", R.string.param_tts_text, ParamType.STRING, true, "Hello World"),
            ParamSpec("language", R.string.param_language, ParamType.STRING, false, "en"),
        )),
        TutuCommandSpec(R.string.subcat_communication_media, R.string.cmd_vibrate, "vibrate", listOf(
            ParamSpec("durationMs", R.string.param_vibrate_duration, ParamType.INT, false, "500"),
        )),
        TutuCommandSpec(R.string.subcat_communication_media, R.string.cmd_search_media, "search_media", listOf(
            ParamSpec("query", R.string.param_query, ParamType.STRING, false, ""),
            ParamSpec("mediaType", R.string.param_media_type, ParamType.STRING, false, ""),
            ParamSpec("limit", R.string.param_limit, ParamType.INT, false, "20"),
        )),
    ))
    // endregion

    // region 9. Lock & Security
    private fun lockAndSecurity() = CommandCategory(R.string.cat_lock_security, listOf(
        TutuCommandSpec(R.string.subcat_lock_security, R.string.cmd_get_lock_status, "get_lock_status"),
        TutuCommandSpec(R.string.subcat_lock_security, R.string.cmd_unlock_screen, "unlock_screen", listOf(
            ParamSpec("credential", R.string.param_credential, ParamType.STRING, false, ""),
        )),
        TutuCommandSpec(R.string.subcat_lock_security, R.string.cmd_set_lock_password, "set_lock_password", listOf(
            ParamSpec("lockType", R.string.param_lock_type, ParamType.STRING, true, "pin"),
            ParamSpec("newCredential", R.string.param_new_credential, ParamType.STRING, true, "1234"),
            ParamSpec("oldCredential", R.string.param_old_credential, ParamType.STRING, false, ""),
        )),
        TutuCommandSpec(R.string.subcat_lock_security, R.string.cmd_clear_lock_password, "clear_lock_password", listOf(
            ParamSpec("oldCredential", R.string.param_current_password, ParamType.STRING, false, ""),
        )),
    ))
    // endregion

    // region 10. System Info
    private fun systemInfo() = CommandCategory(R.string.cat_system_info, listOf(
        TutuCommandSpec(R.string.subcat_system_info, R.string.cmd_get_server_info, "get_server_info"),
        TutuCommandSpec(R.string.subcat_system_info, R.string.cmd_get_running_processes, "get_running_processes", listOf(
            ParamSpec("appsOnly", R.string.param_apps_only, ParamType.BOOLEAN, false, "false"),
        )),
        TutuCommandSpec(R.string.subcat_system_info, R.string.cmd_get_battery_stats, "get_battery_stats"),
        TutuCommandSpec(R.string.subcat_system_info, R.string.cmd_logcat, "logcat", listOf(
            ParamSpec("filter", R.string.param_filter_tag, ParamType.STRING, false, ""),
            ParamSpec("lines", R.string.param_lines, ParamType.INT, false, "100"),
            ParamSpec("level", R.string.param_log_level, ParamType.STRING, false, ""),
        )),
    ))
    // endregion

    // region 11. Network & Peripheral
    private fun networkAndPeripheral() = CommandCategory(R.string.cat_network_peripheral, listOf(
        TutuCommandSpec(R.string.subcat_network_peripheral, R.string.cmd_get_wifi_list, "get_wifi_list"),
        TutuCommandSpec(R.string.subcat_network_peripheral, R.string.cmd_set_airplane_mode, "set_airplane_mode", listOf(
            ParamSpec("enabled", R.string.param_enabled, ParamType.BOOLEAN, true, "true"),
        )),
        TutuCommandSpec(R.string.subcat_network_peripheral, R.string.cmd_set_screen_timeout, "set_screen_timeout", listOf(
            ParamSpec("timeoutMs", R.string.param_timeout_ms, ParamType.INT, true, "60000"),
        )),
    ))
    // endregion

    // region 12. Notification & Media Files
    private fun notificationAndMedia() = CommandCategory(R.string.cat_notification_media, listOf(
        TutuCommandSpec(R.string.subcat_notification_media, R.string.cmd_push_notification, "push_notification", listOf(
            ParamSpec("title", R.string.param_title, ParamType.STRING, true, "Test"),
            ParamSpec("text", R.string.param_content, ParamType.STRING, true, "This is a test notification"),
        )),
        TutuCommandSpec(R.string.subcat_notification_media, R.string.cmd_set_wallpaper, "set_wallpaper", listOf(
            ParamSpec("path", R.string.param_image_path, ParamType.STRING, true, "/sdcard/wallpaper.jpg"),
            ParamSpec("which", R.string.param_wallpaper_which, ParamType.STRING, false, "home"),
        )),
        TutuCommandSpec(R.string.subcat_notification_media, R.string.cmd_take_screenshot_to_file, "take_screenshot_to_file", listOf(
            ParamSpec("path", R.string.param_save_path, ParamType.STRING, false, "/sdcard/screenshot.png"),
        )),
        TutuCommandSpec(R.string.subcat_notification_media, R.string.cmd_record_screen, "record_screen", listOf(
            ParamSpec("path", R.string.param_save_path, ParamType.STRING, false, "/sdcard/record.mp4"),
            ParamSpec("durationSec", R.string.param_record_duration, ParamType.INT, false, "10"),
            ParamSpec("bitRate", R.string.param_record_bit_rate, ParamType.INT, false, "0"),
        )),
    ))
    // endregion

    // region 13. Service Control
    private fun serviceControl() = CommandCategory(R.string.cat_service_control, listOf(
        TutuCommandSpec(R.string.subcat_service_control, R.string.cmd_get_ui_nodes, "get_ui_nodes", listOf(
            ParamSpec("mode", R.string.param_ui_nodes_mode, ParamType.INT, false, "2"),
        ), hasResponse = false),
        TutuCommandSpec(R.string.subcat_service_control, R.string.cmd_get_device_info, "get_device_info", hasResponse = false),
        TutuCommandSpec(R.string.subcat_service_control, R.string.cmd_shutdown, "shutdown"),
    ))
    // endregion

    // region 14. Virtual Display
    private fun virtualDisplay() = CommandCategory(R.string.cat_virtual_display, listOf(
        TutuCommandSpec(R.string.subcat_virtual_display, R.string.cmd_vd_create, "vd_create", listOf(
            ParamSpec("width", R.string.param_vd_width, ParamType.INT, false, "1080"),
            ParamSpec("height", R.string.param_vd_height, ParamType.INT, false, "1920"),
            ParamSpec("dpi", R.string.param_vd_dpi, ParamType.INT, false, "420"),
            ParamSpec("systemDecorations", R.string.param_system_decorations, ParamType.BOOLEAN, false, "false"),
        )),
        TutuCommandSpec(R.string.subcat_virtual_display, R.string.cmd_vd_destroy, "vd_destroy", listOf(
            ParamSpec("displayId", R.string.param_display_id, ParamType.INT, true, ""),
        )),
        TutuCommandSpec(R.string.subcat_virtual_display, R.string.cmd_vd_list, "vd_list"),
        TutuCommandSpec(R.string.subcat_virtual_display, R.string.cmd_vd_start_app, "vd_start_app", listOf(
            ParamSpec("displayId", R.string.param_display_id, ParamType.INT, true, ""),
            ParamSpec("package", R.string.param_package, ParamType.STRING, true, "com.android.settings"),
            ParamSpec("forceStop", R.string.param_force_stop, ParamType.BOOLEAN, false, "false"),
        )),
        TutuCommandSpec(R.string.subcat_virtual_display, R.string.cmd_vd_screenshot, "vd_screenshot", listOf(
            ParamSpec("displayId", R.string.param_display_id, ParamType.INT, false, "0"),
            ParamSpec("maxSize", R.string.param_max_size, ParamType.INT, false, "1080"),
            ParamSpec("quality", R.string.param_quality, ParamType.INT, false, "80"),
        )),
        TutuCommandSpec(R.string.subcat_virtual_display, R.string.cmd_vd_touch, "vd_touch", listOf(
            ParamSpec("displayId", R.string.param_display_id, ParamType.INT, true, ""),
            ParamSpec("action", R.string.param_vd_action, ParamType.INT, true, "0"),
            ParamSpec("x", R.string.param_x, ParamType.INT, true, "540"),
            ParamSpec("y", R.string.param_y, ParamType.INT, true, "960"),
            ParamSpec("pressure", R.string.param_pressure, ParamType.FLOAT, false, "1.0"),
            ParamSpec("pointerId", R.string.param_pointer_id, ParamType.LONG, false, "0"),
        ), hasResponse = false, needsDelay = true),
        TutuCommandSpec(R.string.subcat_virtual_display, R.string.cmd_vd_key, "vd_key", listOf(
            ParamSpec("displayId", R.string.param_display_id, ParamType.INT, true, ""),
            ParamSpec("keycode", R.string.param_keycode, ParamType.INT, true, "4"),
            ParamSpec("action", R.string.param_vd_key_action, ParamType.INT, false, ""),
            ParamSpec("repeat", R.string.param_repeat, ParamType.INT, false, "0"),
            ParamSpec("metaState", R.string.param_meta_state, ParamType.INT, false, "0"),
        ), hasResponse = false, needsDelay = true),
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
