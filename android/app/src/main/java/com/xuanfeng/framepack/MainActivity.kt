/*
 * XF FramePack
 * Copyright (c) 2026 xuanfeng0316
 * Licensed under GPL 3.0
 */

package com.xuanfeng.framepack

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.InputType
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.io.File

class MainActivity : Activity() {

    private lateinit var inputEdit: EditText
    private lateinit var outputEdit: EditText
    private lateinit var thresholdEdit: EditText
    private lateinit var startButton: Button

    private val handler = Handler(Looper.getMainLooper())
    private var canceller: FrameExtractor.Canceller? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildUI())
    }

    private fun buildUI(): View {
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setPadding(40, 40, 40, 40)

        root.addView(label("XF FramePack"))

        root.addView(label("输入文件绝对路径"))
        inputEdit = EditText(this)
        inputEdit.hint = "/sdcard/video.mp4"
        inputEdit.inputType = InputType.TYPE_TEXT_VARIATION_URI
        root.addView(inputEdit)

        root.addView(label("输出文件绝对路径"))
        outputEdit = EditText(this)
        outputEdit.hint = "/sdcard/output.zip"
        outputEdit.inputType = InputType.TYPE_TEXT_VARIATION_URI
        root.addView(outputEdit)

        root.addView(label("跳帧判断阈值"))
        thresholdEdit = EditText(this)
        thresholdEdit.hint = "0"
        thresholdEdit.inputType = InputType.TYPE_CLASS_NUMBER
        root.addView(thresholdEdit)

        startButton = Button(this)
        startButton.text = "开始转换"
        startButton.setOnClickListener { onStartClicked() }
        root.addView(startButton)

        return ScrollView(this).apply { addView(root) }
    }

    private fun label(text: String): TextView {
        return TextView(this).apply {
            this.text = text
            textSize = 16f
            setPadding(0, 20, 0, 8)
        }
    }

    private fun onStartClicked() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                intent.data = Uri.parse("package:$packageName")
                startActivity(intent)
                return
            }
        }
        startConvert()
    }

    private fun startConvert() {
        val inputPath = inputEdit.text.toString().trim()
        val outputPath = outputEdit.text.toString().trim()

        if (inputPath.isEmpty() || outputPath.isEmpty()) {
            Toast.makeText(this, "路径不能为空", Toast.LENGTH_SHORT).show()
            return
        }

        val inputFile = File(inputPath)
        if (!inputFile.exists()) {
            Toast.makeText(this, "输入文件不存在", Toast.LENGTH_SHORT).show()
            return
        }

        val threshold = thresholdEdit.text.toString().trim().toDoubleOrNull() ?: 0.0
        val outputFile = File(outputPath)

        if (BFrameDetector.hasBFrames(inputFile)) {
            AlertDialog.Builder(this)
                .setMessage("视频含有 B 帧，帧顺序可能出错，是否继续？")
                .setPositiveButton("继续") { _, _ ->
                    showProgressDialog(inputFile, outputFile, threshold)
                }
                .setNegativeButton("取消", null)
                .show()
            return
        }

        showProgressDialog(inputFile, outputFile, threshold)
    }

    private fun showProgressDialog(input: File, output: File, threshold: Double) {
        val text = TextView(this).apply {
            typeface = Typeface.MONOSPACE
            textSize = 14f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.BLACK)
            setPadding(30, 30, 30, 30)
        }

        val dialog = AlertDialog.Builder(this)
            .setView(text)
            .setCancelable(false)
            .setNegativeButton("取消") { _, _ ->
                canceller?.cancel()
            }
            .create()
        dialog.show()

        canceller = FrameExtractor.Canceller()
        val c = canceller!!

        startButton.isEnabled = false

        Thread {
            try {
                FrameExtractor.extract(input, output, "png", threshold, 0,
                    { current, total, bytes ->
                        val percent = if (total > 0) current * 100.0 / total else 0.0
                        val bar = buildBar(percent)
                        val line = "转换 $current/$total ${String.format("%.2f", percent)}% ${formatSize(bytes)}"
                        handler.post {
                            text.text = line + "\n" + bar
                        }
                    }, c)
                handler.post {
                    dialog.dismiss()
                    startButton.isEnabled = true
                    if (c.cancelled) {
                        Toast.makeText(this, "已取消", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this, "完成", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                handler.post {
                    dialog.dismiss()
                    startButton.isEnabled = true
                    Toast.makeText(this, "错误: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    private fun buildBar(percent: Double): String {
        val total = 20
        val filled = (percent / 100.0 * total).toInt()
        val sb = StringBuilder("[")
        for (i in 0 until total) {
            sb.append(if (i < filled) '#' else '.')
        }
        sb.append("]")
        return sb.toString()
    }

    private fun formatSize(bytes: Long): String {
        if (bytes < 0) return "0B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB", "PB", "EB", "ZB", "YB", "BB", "NB")
        if (bytes < 1024) return "${bytes}B"
        var value = bytes.toDouble()
        var index = 0
        while (value >= 1024 && index < units.size - 1) {
            value /= 1024
            index++
        }
        return String.format("%.2f%s", value, units[index])
    }
}