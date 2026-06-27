package com.example.bequiet

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    // 以前のローカル変数 isMonitoring を廃止し、プロパティで状態を動的に管理
    private val isServiceRunning: Boolean
        get() = VolumeAnchorService.isRunning

    private var selectedVolume = 0
    private var selectedDurationMinutes = 0

    // UIパーツをクラス全体のプロパティにして、後から更新できるようにする
    private lateinit var statusTextView: TextView
    private lateinit var actionButton: Button
    private lateinit var volumeLabel: TextView

    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)

        // 初期値の決定：サービスが動いていればその設定値を、動いていなければ現在の端末音量を採用
        selectedVolume = if (isServiceRunning) {
            VolumeAnchorService.targetVolume
        } else {
            audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        }

        // --- 画面UI構築 ---
        val rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.WHITE)
            setPadding(40, 40, 40, 40)
        }

        statusTextView = TextView(this).apply {
            textSize = 18f
            setTextColor(Color.BLACK)
            gravity = Gravity.CENTER
        }

        volumeLabel = TextView(this).apply {
            text = "固定する音量: $selectedVolume"
            textSize = 16f
            setPadding(0, 40, 0, 10)
        }

        val volumeSeekBar = SeekBar(this).apply {
            max = maxVolume
            progress = selectedVolume
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    selectedVolume = progress
                    volumeLabel.text = "固定する音量: $selectedVolume"

                    // ★改善ポイント：常駐中にスライダーを動かしたら、リアルタイムでサービスに通知
                    if (isServiceRunning) {
                        updateServiceSettings()
                    }
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }

        val timerLabel = TextView(this).apply {
            text = "自動解除タイマー: なし (常駐しっぱなし)"
            textSize = 16f
            setPadding(0, 40, 0, 10)
        }
        val timerSeekBar = SeekBar(this).apply {
            max = 4
            progress = 0
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    selectedDurationMinutes = when(progress) {
                        1 -> 5
                        2 -> 10
                        3 -> 30
                        4 -> 60
                        else -> 0
                    }
                    timerLabel.text = if (selectedDurationMinutes > 0) {
                        "自動解除タイマー: $selectedDurationMinutes 分後に自動停止"
                    } else {
                        "自動解除タイマー: なし (常駐しっぱなし)"
                    }

                    // ★タイマー変更も、もし起動中ならリアルタイム反映（必要に応じて）
                    if (isServiceRunning) {
                        updateServiceSettings()
                    }
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }

        actionButton = Button(this).apply {
            textSize = 16f
        }

        actionButton.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 101)
                    return@setOnClickListener
                }
            }

            val serviceIntent = Intent(this, VolumeAnchorService::class.java).apply {
                putExtra(VolumeAnchorService.EXTRA_TARGET_VOLUME, selectedVolume)
                putExtra(VolumeAnchorService.EXTRA_DURATION_MINUTES, selectedDurationMinutes)
            }

            // システムのフラグが変わるのを待たずに、ボタンを押したアクションそのもので画面を確定させる
            if (!isServiceRunning) {
                // 【これから起動する場合】
                ContextCompat.startForegroundService(this, serviceIntent)
                Toast.makeText(this, "サービスを開始しました", Toast.LENGTH_SHORT).show()

                // 画面をその場で「動作中」の表示に強制変更
                statusTextView.text = "指定音量 ($selectedVolume) に固定中..."
                actionButton.text = "サービスを停止"
                actionButton.setBackgroundColor(Color.RED)
                actionButton.setTextColor(Color.WHITE)
            } else {
                // 【これから停止する場合】
                stopService(serviceIntent)
                Toast.makeText(this, "サービスを停止しました", Toast.LENGTH_SHORT).show()

                // 画面をその場で「停止」の表示に強制変更
                statusTextView.text = "サービスは停止しています"
                actionButton.text = "常駐サービスを開始"
                actionButton.setBackgroundColor(Color.LTGRAY)
                actionButton.setTextColor(Color.BLACK)
            }
        }

        rootLayout.addView(statusTextView)
        rootLayout.addView(volumeLabel)
        rootLayout.addView(volumeSeekBar)
        rootLayout.addView(timerLabel)
        rootLayout.addView(timerSeekBar)
        rootLayout.addView(actionButton)
        setContentView(rootLayout)
    }

    // ★追加：画面に戻ってきた時に呼ばれるライフサイクルメソッド
    override fun onResume() {
        super.onResume()
        // タスクキルから戻った時や、通知からアプリを開いた時にUIを正しい状態にする
        refreshUI()
    }

    // ★追加：現在のサービスの状態に合わせて画面のテキストやボタン色を変える
    @SuppressLint("SetTextI18n")
    private fun refreshUI() {
        if (isServiceRunning) {
            statusTextView.text = "指定音量 (${VolumeAnchorService.targetVolume}) に固定中..."
            actionButton.text = "サービスを停止"
            actionButton.setBackgroundColor(Color.RED)
            actionButton.setTextColor(Color.WHITE)
        } else {
            statusTextView.text = "サービスは停止しています"
            actionButton.text = "常駐サービスを開始"
            actionButton.setBackgroundColor(Color.LTGRAY)
            actionButton.setTextColor(Color.BLACK)
        }
    }

    // ★追加：稼働中のサービスへ新しい設定値を送りつける関数
    @SuppressLint("SetTextI18n")
    private fun updateServiceSettings() {
        val serviceIntent = Intent(this, VolumeAnchorService::class.java).apply {
            putExtra(VolumeAnchorService.EXTRA_TARGET_VOLUME, selectedVolume)
            putExtra(VolumeAnchorService.EXTRA_DURATION_MINUTES, selectedDurationMinutes)
        }
        // 既に起動しているサービスに対してstartForegroundServiceを呼ぶと、
        // 新しいIntentを持ったonStartCommandがもう一度裏で走ります（サービスは重複起動しません）
        ContextCompat.startForegroundService(this, serviceIntent)
        statusTextView.text = "指定音量 ($selectedVolume) に固定中..."
    }
}