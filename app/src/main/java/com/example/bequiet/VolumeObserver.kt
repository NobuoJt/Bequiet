package com.example.bequiet

import android.content.Context
import android.database.ContentObserver
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log

class VolumeObserver(
    private val context: Context
) {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    // 音量の変更を検知するリスナー
    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            super.onChange(selfChange)

            val target = VolumeAnchorService.targetVolume
            val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)

            // 現在の音量が、ターゲットにしている固定値と異なる場合、引き戻す
            if (currentVolume != target) {
                Log.d("VolumeObserver", "音量のズレを検知: $currentVolume -> $target に固定します")
                audioManager.setStreamVolume(
                    AudioManager.STREAM_MUSIC,
                    target,
                    0 // UIを出さない
                )
            }
        }
    }

    // 監視を開始するメソッド
    fun startMonitoring() {
        context.contentResolver.registerContentObserver(
            Settings.System.CONTENT_URI,
            true,
            observer
        )
        // 開始時（または設定変更時）に最新のターゲット音量を即座に適用
        val target = VolumeAnchorService.targetVolume
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0)
        Log.d("VolumeObserver", "音量監視を開始/更新しました。目標音量: $target")
    }

    // 監視を停止するメソッド
    fun stopMonitoring() {
        context.contentResolver.unregisterContentObserver(observer)
        Log.d("VolumeObserver", "音量監視を停止しました。")
    }
}