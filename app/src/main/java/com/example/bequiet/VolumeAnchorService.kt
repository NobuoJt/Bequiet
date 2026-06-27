package com.example.bequiet

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat

class VolumeAnchorService : Service() {

    private var volumeObserver: VolumeObserver? = null
    private val CHANNEL_ID = "volume_monitor_channel"
    private val NOTIFICATION_ID = 1

    private val handler = Handler(Looper.getMainLooper())
    private var stopRunnable: Runnable? = null

    companion object {
        const val ACTION_STOP = "com.example.bequiet.ACTION_STOP"
        const val EXTRA_TARGET_VOLUME = "extra_target_volume"
        const val EXTRA_DURATION_MINUTES = "extra_duration_minutes"

        var targetVolume = 0

        var isRunning = false
    }

    override fun onCreate() {
        super.onCreate()
        volumeObserver = VolumeObserver(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }

        // ★追加：起動フラグをtrueにする
        isRunning = true

        // 画面から渡された設定値を取得
        targetVolume = intent?.getIntExtra(EXTRA_TARGET_VOLUME, 0) ?: 0
        val durationMinutes = intent?.getIntExtra(EXTRA_DURATION_MINUTES, 0) ?: 0

        // --- 以前実装した、通知作成やタイマーセットの処理がここに入ります ---
        // （変更をリアルタイム反映するために、毎回通知を再生成して startForeground を叩き直す仕様になります）
        createNotificationChannel()

        val openIntent = Intent(this, MainActivity::class.java)
        val openPendingIntent = PendingIntent.getActivity(
            this, 0, openIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stopIntent = Intent(this, VolumeAnchorService::class.java).apply { action = ACTION_STOP }
        val stopPendingIntent = PendingIntent.getService(
            this, 1, stopIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val timerText = if (durationMinutes > 0) "（残り約 ${durationMinutes} 分で自動解除）" else ""

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Be quiet 稼働中")
            .setContentText("メディア音量を $targetVolume に固定しています$timerText")
            .setSmallIcon(android.R.drawable.ic_lock_silent_mode)
            .setContentIntent(openPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "一発停止", stopPendingIntent)
            .build()

        startForeground(NOTIFICATION_ID, notification)

        // 監視の開始
        volumeObserver?.startMonitoring()

        // 既存のタイマーをクリアして再セット
        stopRunnable?.let { handler.removeCallbacks(it) }
        if (durationMinutes > 0) {
            stopRunnable = Runnable { stopSelf() }
            handler.postDelayed(stopRunnable!!, durationMinutes * 60 * 1000L)
        }

        return START_STICKY
    }

    override fun onDestroy() {
        // ★追加：終了時にフラグをfalseにする
        isRunning = false
        volumeObserver?.stopMonitoring()
        stopRunnable?.let { handler.removeCallbacks(it) }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "音量監視サービス",
                NotificationManager.IMPORTANCE_LOW // LOWにすると通知がサイレントになり、設定からさらに最小化可能
            ).apply {
                description = "設定された音量を維持するサービスです"
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }
}