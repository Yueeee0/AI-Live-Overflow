package com.yueeee0.deskpet

import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private val PREFS = "deskpet_prefs"
    private val KEY_SIZE = "pet_size"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)

        findViewById<Button>(R.id.btn_start).setOnClickListener {
            if (checkOverlayPermission()) {
                startService(Intent(this, OverlayService::class.java))
                Toast.makeText(this, "小渡出来了", Toast.LENGTH_SHORT).show()
            } else {
                requestOverlayPermission()
            }
        }

        findViewById<Button>(R.id.btn_stop).setOnClickListener {
            stopService(Intent(this, OverlayService::class.java))
            Toast.makeText(this, "小渡回去了", Toast.LENGTH_SHORT).show()
        }

        findViewById<Button>(R.id.btn_usage).setOnClickListener {
            try {
                startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
            } catch (_: Exception) {
                Toast.makeText(this, "请在设置里搜索「使用情况访问」", Toast.LENGTH_LONG).show()
            }
        }

        // 大小调节
        val seek = findViewById<SeekBar>(R.id.seek_size)
        val sizeLabel = findViewById<TextView>(R.id.size_label)
        val current = prefs.getInt(KEY_SIZE, 130)
        seek.progress = current - 80
        sizeLabel.text = "大小：$current"
        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar?, progress: Int, fromUser: Boolean) {
                val size = progress + 80
                sizeLabel.text = "大小：$size"
                prefs.edit().putInt(KEY_SIZE, size).apply()
            }
            override fun onStartTrackingTouch(bar: SeekBar?) {}
            override fun onStopTrackingTouch(bar: SeekBar?) {
                stopService(Intent(this@MainActivity, OverlayService::class.java))
                startService(Intent(this@MainActivity, OverlayService::class.java))
                Toast.makeText(this@MainActivity, "大小已更新", Toast.LENGTH_SHORT).show()
            }
        })

        if (checkOverlayPermission()) {
            startService(Intent(this, OverlayService::class.java))
        }
    }

    private fun checkOverlayPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(this)
        } else true
    }

    private fun requestOverlayPermission() {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName")
        )
        startActivityForResult(intent, 100)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 100 && checkOverlayPermission()) {
            startService(Intent(this, OverlayService::class.java))
            Toast.makeText(this, "小渡出来了", Toast.LENGTH_SHORT).show()
        }
    }
}
