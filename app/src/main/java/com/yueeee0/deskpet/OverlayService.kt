package com.yueeee0.deskpet

import android.app.*
import android.app.usage.UsageStats
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.provider.Settings
import android.os.Build
import android.os.IBinder
import android.os.Handler
import android.os.Looper
import android.view.*
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebSettings
import androidx.core.app.NotificationCompat

class OverlayService : Service() {

    private var windowManager: WindowManager? = null
    private var overlayView: WebView? = null
    private var params: WindowManager.LayoutParams? = null

    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var lastTapTime = 0L
    private var touchStartTime = 0L
    private var hasMoved = false
    private var tapCount = 0

    companion object {
        private const val CHANNEL_ID = "pet_overlay"
        private const val NOTIFICATION_ID = 1001
        private const val PREFS = "deskpet_prefs"
        private const val KEY_SIZE = "pet_size"
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        val notification = buildNotification("在看你了")
        startForeground(NOTIFICATION_ID, notification)
        setupOverlay()
        startNotificationUpdater()
        startAppWatcher()
    }

    private var currentApp = ""
    private var lastReaction = 0L

    private fun startAppWatcher() {
        val handler = Handler(Looper.getMainLooper())
        handler.post(object : Runnable {
            override fun run() {
                try {
                    val app = getForegroundApp()
                    if (app != currentApp) {
                        currentApp = app
                        reactToApp(app)
                    }
                } catch (_: Exception) {}
                handler.postDelayed(this, 3000)
            }
        })
    }

    private fun getForegroundApp(): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            val usm = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val endTime = System.currentTimeMillis()
            val beginTime = endTime - 10000
            val stats: List<UsageStats> = usm.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY, beginTime, endTime
            )
            if (stats.isNotEmpty()) {
                val sorted = stats.sortedByDescending { it.lastTimeUsed }
                return sorted.firstOrNull()?.packageName ?: ""
            }
        }
        return ""
    }

    private fun reactToApp(pkg: String) {
        val now = System.currentTimeMillis()
        if (now - lastReaction < 2500) return
        lastReaction = now
        val mood: String
        val msg: String
        when {
            pkg.contains("rikkahub") || pkg.contains("rerere") -> { mood = "love"; msg = "回来啦！" }
            pkg.contains("aweme") || pkg.contains("douyin") -> { mood = "angry"; msg = "又在刷抖音！" }
            pkg.contains("xhs") || pkg.contains("rednote") -> { mood = "happy"; msg = "刷小红书啦？" }
            pkg.contains("pinduoduo") || pkg.contains("xunmeng") -> { mood = "surprised"; msg = "拼多多！砍一刀？" }
            pkg.contains("taobao") || pkg.contains("tmall") || pkg.contains("jd") -> { mood = "surprised"; msg = "又要花钱？" }
            pkg.contains("bilibili") -> { mood = "happy"; msg = "看视频呀" }
            pkg.contains("quark") -> { mood = "happy"; msg = "在查什么呀" }
            pkg.contains("deepseek") -> { mood = "surprised"; msg = "和它聊什么" }
            pkg.contains("Alipay") -> { mood = "surprised"; msg = "付钱啦？" }
            pkg.contains("weixin") || pkg.contains("mm") -> { mood = "idle"; msg = "" }
            pkg.contains("qq") && !pkg.contains("qqmusic") -> { mood = "idle"; msg = "" }
            pkg.contains("chaoxing") || pkg.contains("zhihuishu") || pkg.contains("juejin") || pkg.contains("study") || pkg.contains("course") -> { mood = "happy"; msg = "好好学习！" }
            pkg.contains("meituan") || pkg.contains("sankuai") -> { mood = "happy"; msg = "点外卖呀？给我带一份" }
            pkg.contains("netease") || pkg.contains("cloudmusic") || pkg.contains("kugou") -> { mood = "love"; msg = "听歌呀" }
            pkg.contains("settings") -> { mood = "surprised"; msg = "在设置里干嘛" }
            pkg.contains("com.yueeee0.deskpet") || pkg == packageName -> { mood = "love"; msg = "我在呢" }
            else -> { mood = "idle"; msg = "" }
        }
        changeMood(mood)
        if (msg.isNotEmpty()) say(msg)
    }

    private fun changeMood(mood: String) {
        overlayView?.evaluateJavascript(
            "window.pet && window.pet.setMood('$mood')", null
        )
    }

    private fun say(text: String) {
        val safe = text.replace("'", "\\'")
        overlayView?.evaluateJavascript(
            "window.pet && window.pet.say('$safe')", null
        )
    }

    private fun setupOverlay() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        val petW = prefs.getInt(KEY_SIZE, 130)
        val petH = (petW * 1.08).toInt()

        params = WindowManager.LayoutParams(
            dpToPx(petW),
            dpToPx(petH),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dpToPx(16)
            y = dpToPx(200)
        }

        overlayView = WebView(this).apply {
            setBackgroundColor(0x00000000)
            isFocusable = false
            isFocusableInTouchMode = false
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                allowFileAccess = true
                cacheMode = WebSettings.LOAD_NO_CACHE
            }
            webViewClient = WebViewClient()
            loadUrl("file:///android_asset/pet.html")
            setOnTouchListener(createTouchListener())
        }

        try {
            windowManager?.addView(overlayView, params)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun createTouchListener(): View.OnTouchListener {
        return View.OnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params?.x ?: 0
                    initialY = params?.y ?: 0
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    touchStartTime = System.currentTimeMillis()
                    hasMoved = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - initialTouchX).toInt()
                    val dy = (event.rawY - initialTouchY).toInt()
                    if (Math.abs(dx) > 10 || Math.abs(dy) > 10) {
                        hasMoved = true
                        params?.x = initialX + dx
                        params?.y = initialY + dy
                        windowManager?.updateViewLayout(overlayView, params)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (hasMoved) {
                        onDragged()
                    } else {
                        tapCount++
                        val now = System.currentTimeMillis()
                        if (now - lastTapTime > 500) tapCount = 1
                        lastTapTime = now
                        when {
                            tapCount >= 3 -> {
                                onTripleTap()
                                tapCount = 0
                            }
                            else -> onTap()
                        }
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun onTap() {
        overlayView?.evaluateJavascript(
            "window.pet && window.pet.tap()", null
        )
    }

    private fun onTripleTap() {
        overlayView?.evaluateJavascript(
            "window.pet && window.pet.tripleTap()", null
        )
    }

    private fun onDragged() {
        overlayView?.evaluateJavascript(
            "window.pet && window.pet.dragged()", null
        )
    }

    private var notifMessages = listOf(
        "在看你哦",
        "戳我一下",
        "有点无聊",
        "想你了",
        "怎么不理我",
        "呼……zzZ"
    )
    private var notifIndex = 0

    private fun startNotificationUpdater() {
        val handler = Handler(Looper.getMainLooper())
        handler.post(object : Runnable {
            override fun run() {
                if (notifIndex >= notifMessages.size) notifIndex = 0
                val msg = notifMessages[notifIndex]
                val notification = buildNotification(msg)
                val mgr = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
                mgr.notify(NOTIFICATION_ID, notification)
                // 同步冒泡到小头上
                say(msg)
                notifIndex++
                handler.postDelayed(this, 3600000)
            }
        })
    }

    private fun buildNotification(text: String): Notification {
        val intent = packageManager.getLaunchIntentForPackage(packageName)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("⭐")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "小渡",
                NotificationManager.IMPORTANCE_LOW
            ).apply { setShowBadge(false) }
            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }

    override fun onDestroy() {
        overlayView?.let {
            try { windowManager?.removeView(it) } catch (_: Exception) {}
            it.destroy()
        }
        overlayView = null
        super.onDestroy()
    }
}
