package com.example.anki

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.SoundPool
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.SeekBar
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.findNavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import com.example.anki.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var binding: ActivityMainBinding

    private var mediaPlayer: MediaPlayer? = null
    private var soundPool: SoundPool? = null
    private var seSoundId: Int = 0

    private var bgmVolume = 0.5f
    private var seVolume = 0.5f

    // ★追加：現在再生中のBGMリソースを記憶しておく変数
    private var currentBgmResId = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment_content_main) as NavHostFragment
        val navController = navHostFragment.navController
        appBarConfiguration = AppBarConfiguration(navController.graph)
        setupActionBarWithNavController(navController, appBarConfiguration)

        binding.fab.visibility = View.GONE

        // 音量設定の読み込み
        val prefs = getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
        bgmVolume = prefs.getFloat("BGM_VOLUME", 0.5f)
        seVolume = prefs.getFloat("SE_VOLUME", 0.5f)

        // SEの準備
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        soundPool = SoundPool.Builder()
            .setMaxStreams(5)
            .setAudioAttributes(audioAttributes)
            .build()

        // 🔙ボタンを使わせるため、スワイプ戻りを横取りしてダイアログを出す
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                showExitConfirmDialog()
            }
        })
    }

    // ★追加：BGMを切り替える専用の関数（各画面から呼び出します）
    fun switchBgm(bgmResId: Int) {
        // 同じ曲が指定された場合は何もしない（途切れ防止）
        if (currentBgmResId == bgmResId && mediaPlayer?.isPlaying == true) return

        mediaPlayer?.stop()
        mediaPlayer?.release()

        mediaPlayer = MediaPlayer.create(this, bgmResId)
        mediaPlayer?.isLooping = true
        mediaPlayer?.setVolume(bgmVolume, bgmVolume)
        mediaPlayer?.start()

        currentBgmResId = bgmResId
    }

    fun showExitConfirmDialog() {
        AlertDialog.Builder(this)
            .setTitle("確認")
            .setMessage("アプリを終了しますか？")
            .setPositiveButton("終了する") { _, _ -> finish() }
            .setNegativeButton("キャンセル", null)
            .show()
    }

    // ===== ボリューム設定ダイアログ処理など =====
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_settings -> {
                showVolumeDialog()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun showVolumeDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_volume, null)
        val seekBgm = dialogView.findViewById<SeekBar>(R.id.seekBgm)
        val seekSe = dialogView.findViewById<SeekBar>(R.id.seekSe)

        seekBgm.progress = (bgmVolume * 100).toInt()
        seekSe.progress = (seVolume * 100).toInt()

        seekBgm.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                bgmVolume = progress / 100f
                mediaPlayer?.setVolume(bgmVolume, bgmVolume)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        seekSe.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                seVolume = progress / 100f
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        AlertDialog.Builder(this)
            .setTitle("音量設定")
            .setView(dialogView)
            .setPositiveButton("閉じる") { _, _ ->
                getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
                    .edit().putFloat("BGM_VOLUME", bgmVolume).putFloat("SE_VOLUME", seVolume).apply()
            }
            .show()
    }

    override fun onPause() {
        super.onPause()
        if (mediaPlayer?.isPlaying == true) {
            mediaPlayer?.pause()
        }
    }

    override fun onResume() {
        super.onResume()
        mediaPlayer?.start()
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaPlayer?.release()
        mediaPlayer = null
        soundPool?.release()
        soundPool = null
    }

    override fun onSupportNavigateUp(): Boolean {
        val navController = findNavController(R.id.nav_host_fragment_content_main)
        return navController.navigateUp(appBarConfiguration) || super.onSupportNavigateUp()
    }
}