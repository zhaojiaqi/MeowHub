package com.tutu.meowhub.core.settings

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.util.Log

data class AppToolInfo(
    val packageName: String,
    val label: String
)

class AppToolManager(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("app_tools", Context.MODE_PRIVATE)

    // category → list of installed apps
    private var installedByCategory = mutableMapOf<String, MutableList<AppToolInfo>>()
    private var defaults = mutableMapOf<String, String>() // category → packageName

    companion object {
        private const val TAG = "AppToolManager"
        private const val KEY_PREFIX = "default_"

        // All known categories (display order)
        val CATEGORY_NAMES = linkedMapOf(
            "餐饮美食" to "餐饮美食",
            "出行导航" to "出行导航",
            "购物" to "购物",
            "社交通讯" to "社交通讯",
            "支付" to "支付",
            "视频娱乐" to "视频娱乐",
            "音乐" to "音乐",
            "旅行出行" to "旅行出行",
            "生活服务" to "生活服务",
            "工具" to "工具",
            "阅读" to "阅读",
            "办公" to "办公",
            "健康运动" to "健康运动",
            "拍照摄影" to "拍照摄影",
            "教育学习" to "教育学习",
            "金融理财" to "金融理财"
        )

        // packageName → (category, displayName)
        private val KNOWN_APPS = mapOf(
            // ── 餐饮美食 ──
            "com.sankuai.meituan" to ("餐饮美食" to "美团"),
            "com.dianping.v1" to ("餐饮美食" to "大众点评"),
            "me.ele" to ("餐饮美食" to "饿了么"),
            "com.yum.kfc" to ("餐饮美食" to "肯德基"),
            "com.mcdonalds.gma.cn" to ("餐饮美食" to "麦当劳"),
            "com.lucky_coffee" to ("餐饮美食" to "瑞幸咖啡"),
            "com.starbucks.cn" to ("餐饮美食" to "星巴克"),
            "com.heytea.now" to ("餐饮美食" to "喜茶"),
            "com.douguo.recipe" to ("餐饮美食" to "豆果美食"),
            "com.yaya.zone" to ("餐饮美食" to "叮咚买菜"),
            "com.wudaokou.hippo" to ("餐饮美食" to "盒马"),
            "com.pupumall.customer" to ("餐饮美食" to "朴朴超市"),
            "com.pizza.hut.cn" to ("餐饮美食" to "必胜客"),
            "com.nayuki.teahouse" to ("餐饮美食" to "奈雪的茶"),

            // ── 出行导航 ──
            "com.autonavi.minimap" to ("出行导航" to "高德地图"),
            "com.baidu.BaiduMap" to ("出行导航" to "百度地图"),
            "com.google.android.apps.maps" to ("出行导航" to "谷歌地图"),
            "com.sdu.didi.psnger" to ("出行导航" to "滴滴出行"),
            "com.tencent.map" to ("出行导航" to "腾讯地图"),
            "com.huaxiaozhu.rider" to ("出行导航" to "花小猪打车"),
            "com.hellobike.atlas" to ("出行导航" to "哈啰"),
            "com.didapinche.booking" to ("出行导航" to "嘀嗒出行"),
            "com.ly.t3go" to ("出行导航" to "T3出行"),
            "com.caocaokeji.passenger" to ("出行导航" to "曹操出行"),
            "com.waze" to ("出行导航" to "Waze"),

            // ── 购物 ──
            "com.taobao.taobao" to ("购物" to "淘宝"),
            "com.jingdong.app.mall" to ("购物" to "京东"),
            "com.xunmeng.pinduoduo" to ("购物" to "拼多多"),
            "com.tmall.wireless" to ("购物" to "天猫"),
            "com.taobao.idlefish" to ("购物" to "闲鱼"),
            "com.achievo.vipshop" to ("购物" to "唯品会"),
            "com.amazon.mShop.android.shopping" to ("购物" to "Amazon"),
            "com.shizhuang.duapp" to ("购物" to "得物"),
            "com.xingin.xhs" to ("购物" to "小红书"),
            "com.smzdm.client.android" to ("购物" to "什么值得买"),
            "com.alibaba.wireless1688" to ("购物" to "1688"),
            "com.suning.mobile.ebuy" to ("购物" to "苏宁易购"),
            "com.dangdang.buy2" to ("购物" to "当当"),

            // ── 社交通讯 ──
            "com.tencent.mm" to ("社交通讯" to "微信"),
            "com.tencent.mobileqq" to ("社交通讯" to "QQ"),
            "com.tencent.tim" to ("社交通讯" to "TIM"),
            "com.alibaba.android.rimet" to ("社交通讯" to "钉钉"),
            "com.ss.android.lark" to ("社交通讯" to "飞书"),
            "org.telegram.messenger" to ("社交通讯" to "Telegram"),
            "com.whatsapp" to ("社交通讯" to "WhatsApp"),
            "com.facebook.orca" to ("社交通讯" to "Messenger"),
            "com.tencent.wework" to ("社交通讯" to "企业微信"),
            "com.sina.weibo" to ("社交通讯" to "微博"),
            "com.immomo.momo" to ("社交通讯" to "陌陌"),
            "com.p1.mobile.putong" to ("社交通讯" to "探探"),
            "com.discord" to ("社交通讯" to "Discord"),
            "com.slack" to ("社交通讯" to "Slack"),
            "jp.naver.line.android" to ("社交通讯" to "LINE"),
            "org.thoughtcrime.securesms" to ("社交通讯" to "Signal"),
            "com.instagram.android" to ("社交通讯" to "Instagram"),

            // ── 支付 ──
            "com.eg.android.AlipayGphone" to ("支付" to "支付宝"),

            // ── 视频娱乐 ──
            "com.ss.android.ugc.aweme" to ("视频娱乐" to "抖音"),
            "com.ss.android.ugc.aweme.lite" to ("视频娱乐" to "抖音极速版"),
            "com.smile.gifmaker" to ("视频娱乐" to "快手"),
            "com.kuaishou.nebula" to ("视频娱乐" to "快手极速版"),
            "tv.danmaku.bili" to ("视频娱乐" to "bilibili"),
            "com.bilibili.app.in" to ("视频娱乐" to "bilibili国际版"),
            "com.qiyi.video" to ("视频娱乐" to "爱奇艺"),
            "com.youku.phone" to ("视频娱乐" to "优酷"),
            "com.tencent.qqlive" to ("视频娱乐" to "腾讯视频"),
            "com.hunantv.imgo.activity" to ("视频娱乐" to "芒果TV"),
            "com.ss.android.article.video" to ("视频娱乐" to "西瓜视频"),
            "com.ss.android.ugc.live" to ("视频娱乐" to "火山小视频"),
            "com.google.android.youtube" to ("视频娱乐" to "YouTube"),
            "com.netflix.mediaclient" to ("视频娱乐" to "Netflix"),
            "com.zhiliaoapp.musically" to ("视频娱乐" to "TikTok"),
            "tv.twitch.android.app" to ("视频娱乐" to "Twitch"),
            "com.disney.disneyplus" to ("视频娱乐" to "Disney+"),
            "com.tencent.weishi" to ("视频娱乐" to "微视"),

            // ── 音乐 ──
            "com.netease.cloudmusic" to ("音乐" to "网易云音乐"),
            "com.tencent.qqmusic" to ("音乐" to "QQ音乐"),
            "com.kugou.android" to ("音乐" to "酷狗音乐"),
            "cn.kuwo.player" to ("音乐" to "酷我音乐"),
            "com.ximalaya.ting.android" to ("音乐" to "喜马拉雅"),
            "com.spotify.music" to ("音乐" to "Spotify"),
            "com.apple.android.music" to ("音乐" to "Apple Music"),
            "fm.qingting.qtradio" to ("音乐" to "蜻蜓FM"),
            "com.lizhi.tingli" to ("音乐" to "荔枝"),

            // ── 旅行出行 ──
            "com.MobileTicket" to ("旅行出行" to "铁路12306"),
            "ctrip.android.view" to ("旅行出行" to "携程"),
            "com.taobao.trip" to ("旅行出行" to "飞猪"),
            "com.Qunar" to ("旅行出行" to "去哪儿"),
            "com.elong.hotel.elong" to ("旅行出行" to "艺龙"),
            "com.tujia.hotel" to ("旅行出行" to "途家"),
            "com.mfw.roadbook" to ("旅行出行" to "马蜂窝"),
            "com.booking" to ("旅行出行" to "Booking"),
            "com.airbnb.android" to ("旅行出行" to "Airbnb"),
            "com.tongcheng.android" to ("旅行出行" to "同程旅行"),

            // ── 生活服务 ──
            "com.sf.activity" to ("生活服务" to "顺丰速运"),
            "com.ishansong" to ("生活服务" to "闪送"),
            "com.xiaomi.smarthome" to ("生活服务" to "米家"),
            "com.huawei.smarthome" to ("生活服务" to "华为智慧生活"),
            "com.jd.jdlite" to ("生活服务" to "京东到家"),
            "com.fenbi.android.servant" to ("生活服务" to "粉笔"),
            "com.kuaidi.ring" to ("生活服务" to "快递100"),
            "com.cainiao.wireless" to ("生活服务" to "菜鸟"),

            // ── 工具 ──
            "com.tencent.wetype" to ("工具" to "微信输入法"),
            "com.baidu.input" to ("工具" to "百度输入法"),
            "com.sohu.inputmethod.sogou" to ("工具" to "搜狗输入法"),
            "com.google.android.inputmethod.latin" to ("工具" to "Gboard"),
            "com.microsoft.translator" to ("工具" to "微软翻译"),
            "com.google.android.apps.translate" to ("工具" to "Google翻译"),
            "com.youdao.dict" to ("工具" to "有道词典"),
            "com.baidu.translate" to ("工具" to "百度翻译"),
            "com.google.android.calculator" to ("工具" to "计算器"),
            "com.android.calculator2" to ("工具" to "计算器"),
            "com.miui.calculator" to ("工具" to "计算器"),
            "com.google.android.deskclock" to ("工具" to "时钟"),
            "com.android.deskclock" to ("工具" to "时钟"),
            "com.google.android.calendar" to ("工具" to "日历"),
            "com.android.calendar" to ("工具" to "日历"),
            "com.miui.weather2" to ("工具" to "天气"),
            "com.coloros.weather" to ("工具" to "天气"),
            "com.huawei.weather" to ("工具" to "天气"),
            "com.ticktick.task" to ("工具" to "滴答清单"),
            "com.evernote" to ("工具" to "Evernote"),
            "com.google.android.keep" to ("工具" to "Google Keep"),
            "com.miui.notes" to ("工具" to "笔记"),

            // ── 阅读 ──
            "com.douban.frodo" to ("阅读" to "豆瓣"),
            "com.ss.android.article.news" to ("阅读" to "今日头条"),
            "com.ss.android.article.lite" to ("阅读" to "今日头条极速版"),
            "com.zhihu.android" to ("阅读" to "知乎"),
            "com.tencent.weread" to ("阅读" to "微信读书"),
            "com.chaoxing.mobile" to ("阅读" to "学习通"),
            "com.tencent.news" to ("阅读" to "腾讯新闻"),
            "com.netease.newsreader.activity" to ("阅读" to "网易新闻"),
            "com.sina.news" to ("阅读" to "新浪新闻"),
            "com.thepaper.cn" to ("阅读" to "澎湃新闻"),
            "com.ifeng.news2" to ("阅读" to "凤凰新闻"),
            "com.sohu.newsclient" to ("阅读" to "搜狐新闻"),

            // ── 办公 ──
            "cn.wps.moffice_eng" to ("办公" to "WPS Office"),
            "com.microsoft.office.word" to ("办公" to "Word"),
            "com.microsoft.office.excel" to ("办公" to "Excel"),
            "com.microsoft.office.powerpoint" to ("办公" to "PowerPoint"),
            "com.microsoft.office.officehubrow" to ("办公" to "Microsoft 365"),
            "com.google.android.apps.docs" to ("办公" to "Google Docs"),
            "com.google.android.gm" to ("办公" to "Gmail"),
            "com.tencent.wemeet" to ("办公" to "腾讯会议"),
            "us.zoom.videomeetings" to ("办公" to "Zoom"),
            "com.netease.mail" to ("办公" to "网易邮箱"),
            "com.microsoft.teams" to ("办公" to "Teams"),
            "com.notion.id" to ("办公" to "Notion"),

            // ── 健康运动 ──
            "com.gotokeep.keep" to ("健康运动" to "Keep"),
            "com.codoon.gps" to ("健康运动" to "咕咚"),
            "com.thejoyrun.runningman" to ("健康运动" to "悦跑圈"),
            "com.huawei.health" to ("健康运动" to "华为运动健康"),
            "com.xiaomi.hm.health" to ("健康运动" to "小米运动健康"),
            "com.samsung.android.shealth" to ("健康运动" to "三星健康"),
            "com.google.android.apps.fitness" to ("健康运动" to "Google Fit"),
            "com.nike.plusgps" to ("健康运动" to "Nike Run Club"),

            // ── 拍照摄影 ──
            "com.google.android.GoogleCamera" to ("拍照摄影" to "Google相机"),
            "com.android.camera" to ("拍照摄影" to "相机"),
            "com.miui.camera" to ("拍照摄影" to "相机"),
            "com.huawei.camera" to ("拍照摄影" to "相机"),
            "com.meitu.meiyancamera" to ("拍照摄影" to "美颜相机"),
            "com.mt.mtxx.mtxx" to ("拍照摄影" to "美图秀秀"),
            "com.google.android.apps.photos" to ("拍照摄影" to "Google相册"),
            "com.miui.gallery" to ("拍照摄影" to "相册"),

            // ── 教育学习 ──
            "com.duolingo" to ("教育学习" to "多邻国"),
            "com.baidu.homework" to ("教育学习" to "作业帮"),
            "com.yuantiku.jiayuan" to ("教育学习" to "猿辅导"),
            "com.tal.xueersi" to ("教育学习" to "学而思"),
            "com.tencent.edu" to ("教育学习" to "腾讯课堂"),
            "com.netease.edu" to ("教育学习" to "网易公开课"),
            "com.coursera.app" to ("教育学习" to "Coursera"),

            // ── 金融理财 ──
            "com.chinamworld.main" to ("金融理财" to "建设银行"),
            "com.icbc" to ("金融理财" to "工商银行"),
            "cmb.pb" to ("金融理财" to "招商银行"),
            "com.abchina.abc" to ("金融理财" to "农业银行"),
            "com.bankcomm.Bankcomm" to ("金融理财" to "交通银行"),
            "com.hexin.plat.android" to ("金融理财" to "同花顺"),
            "com.eastmoney.android.berlin" to ("金融理财" to "东方财富"),
            "com.xueqiu.android" to ("金融理财" to "雪球"),
            "com.ant.wealth" to ("金融理财" to "蚂蚁财富"),
            "com.lufax.android" to ("金融理财" to "陆金所")
        )
    }

    fun init() {
        scanApps()
    }

    private fun scanApps() {
        installedByCategory.clear()
        defaults.clear()

        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val resolveInfos = pm.queryIntentActivities(intent, 0)
        val installedPackages = mutableSetOf<String>()
        val labelMap = mutableMapOf<String, String>() // pkg → device label

        for (ri in resolveInfos) {
            val pkg = ri.activityInfo.packageName
            installedPackages.add(pkg)
            labelMap[pkg] = ri.loadLabel(pm).toString()
        }

        Log.i(TAG, "Scanned ${installedPackages.size} launcher apps")

        for ((pkg, categoryAndName) in KNOWN_APPS) {
            if (pkg in installedPackages) {
                val (category, knownName) = categoryAndName
                // Use device label if available, fallback to our known name
                val displayLabel = labelMap[pkg] ?: knownName
                installedByCategory.getOrPut(category) { mutableListOf() }
                    .add(AppToolInfo(packageName = pkg, label = displayLabel))
            }
        }

        // Load saved defaults or pick first
        for ((category, apps) in installedByCategory) {
            val saved = prefs.getString(KEY_PREFIX + category, null)
            defaults[category] = if (saved != null && apps.any { it.packageName == saved })
                saved else apps.first().packageName
        }

        Log.i(TAG, "${installedByCategory.size} categories matched")
    }

    fun getCategories(): Map<String, List<AppToolInfo>> {
        if (installedByCategory.isEmpty()) scanApps()
        return installedByCategory
    }

    fun getDefaultApp(category: String): String? {
        if (defaults.isEmpty()) scanApps()
        return defaults[category]
    }

    fun setDefaultApp(category: String, packageName: String) {
        defaults[category] = packageName
        prefs.edit().putString(KEY_PREFIX + category, packageName).apply()
    }

    fun buildToolsContext(): String {
        val categories = getCategories()
        if (categories.isEmpty()) return ""

        val sb = StringBuilder()
        sb.appendLine("\n## 用户设备上已安装的应用")
        sb.appendLine("以下是用户设备上可用的应用。标记 [default] 的是用户偏好的默认应用，请优先使用。")
        sb.appendLine("使用 open_app(package='包名') 来打开应用。")

        for ((category, apps) in categories) {
            val defaultPkg = defaults[category]
            val labeled = apps.joinToString(", ") { app ->
                val tag = if (app.packageName == defaultPkg) " [default]" else ""
                "${app.label}(${app.packageName})$tag"
            }
            sb.appendLine("- $category: $labeled")
        }

        return sb.toString()
    }
}
