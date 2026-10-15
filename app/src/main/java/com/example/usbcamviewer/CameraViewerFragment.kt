package com.example.usbcamviewer

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.jiangdg.ausbc.base.CameraFragment
import com.jiangdg.ausbc.callback.ICaptureCallBack
import com.jiangdg.ausbc.widget.AspectRatioTextureView
import com.jiangdg.ausbc.widget.IAspectRatio
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Monitor / viewer for a UVC (USB Video Class) camera or capture card.
 *
 * - Full screen live picture; tap the picture to show or hide the buttons.
 * - Photo, video recording, USB audio playback and video size picker.
 *
 * Photos and videos are saved to Android/data/com.example.usbcamviewer/files/captures/
 * (no storage permission needed).
 */
class CameraViewerFragment : CameraFragment() {

    private var contentRoot: View? = null
    private var hintText: TextView? = null
    private var statusText: TextView? = null
    private var controlBar: View? = null
    private var recordButton: Button? = null
    private var audioButton: Button? = null

    private var audioMonitor: AudioMonitor? = null
    private var recording = false
    private var controlsVisible = true

    private val hideControlsRunnable = Runnable { setControlsVisible(false) }

    private val ticker = object : Runnable {
        override fun run() {
            refreshStatus()
            contentRoot?.postDelayed(this, 1000)
        }
    }

    override fun getRootView(inflater: LayoutInflater, container: ViewGroup?): View? {
        contentRoot = inflater.inflate(R.layout.fragment_camera, container, false)
        return contentRoot
    }

    override fun getCameraView(): IAspectRatio? = AspectRatioTextureView(requireContext())

    override fun getCameraViewContainer(): ViewGroup? =
        contentRoot?.findViewById(R.id.cameraContainer)

    override fun initView() {
        super.initView()
        val root = contentRoot ?: return

        hintText = root.findViewById(R.id.hintText)
        statusText = root.findViewById(R.id.statusText)
        controlBar = root.findViewById(R.id.controlBar)
        recordButton = root.findViewById(R.id.btnRecord)
        audioButton = root.findViewById(R.id.btnAudio)
        audioMonitor = AudioMonitor(requireContext().applicationContext)

        root.findViewById<Button>(R.id.btnSnapshot).setOnClickListener { takePhoto(); keepControls() }
        recordButton?.setOnClickListener { toggleRecording(); keepControls() }
        audioButton?.setOnClickListener { toggleAudio(); keepControls() }
        root.findViewById<Button>(R.id.btnSize).setOnClickListener { chooseSize() }

        // Tap anywhere on the picture to show or hide the buttons.
        root.setOnClickListener { setControlsVisible(!controlsVisible) }

        root.postDelayed(ticker, 500)
        scheduleHide()
    }

    // ---------- controls visibility ----------

    private fun setControlsVisible(visible: Boolean) {
        controlsVisible = visible
        controlBar?.visibility = if (visible) View.VISIBLE else View.GONE
        refreshStatus()
        if (visible) scheduleHide() else contentRoot?.removeCallbacks(hideControlsRunnable)
    }

    private fun scheduleHide() {
        contentRoot?.removeCallbacks(hideControlsRunnable)
        contentRoot?.postDelayed(hideControlsRunnable, 5000)
    }

    private fun keepControls() = scheduleHide()

    private fun refreshStatus() {
        val opened = isCameraOpened()
        hintText?.visibility = if (opened) View.GONE else View.VISIBLE

        val size = if (opened) getCurrentPreviewSize() else null
        if (size != null && controlsVisible) {
            statusText?.text = "${size.width} x ${size.height}"
            statusText?.visibility = View.VISIBLE
        } else {
            statusText?.visibility = View.GONE
        }

        val audioOn = audioMonitor?.isRunning == true
        audioButton?.setText(if (audioOn) R.string.btn_audio_on else R.string.btn_audio_off)
    }

    // ---------- photo / video ----------

    private fun outputDir(): File {
        val dir = File(requireContext().getExternalFilesDir(null), "captures")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private fun timestamp(): String =
        SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())

    private fun takePhoto() {
        if (!isCameraOpened()) {
            toast(getString(R.string.toast_no_camera))
            return
        }
        val path = File(outputDir(), "IMG_${timestamp()}.jpg").absolutePath
        captureImage(object : ICaptureCallBack {
            override fun onBegin() {}
            override fun onError(error: String?) {
                toast(getString(R.string.toast_error, error ?: "unknown"))
            }
            override fun onComplete(path: String?) {
                toast(getString(R.string.toast_saved, path ?: ""))
            }
        }, path)
    }

    private fun toggleRecording() {
        if (!isCameraOpened()) {
            toast(getString(R.string.toast_no_camera))
            return
        }
        if (!recording) {
            val path = File(outputDir(), "VID_${timestamp()}.mp4").absolutePath
            captureVideoStart(object : ICaptureCallBack {
                override fun onBegin() {
                    recording = true
                    setRecordLabel(R.string.btn_record_stop)
                }
                override fun onError(error: String?) {
                    recording = false
                    setRecordLabel(R.string.btn_record_start)
                    toast(getString(R.string.toast_error, error ?: "unknown"))
                }
                override fun onComplete(path: String?) {
                    recording = false
                    setRecordLabel(R.string.btn_record_start)
                    toast(getString(R.string.toast_saved, path ?: ""))
                }
            }, path)
        } else {
            captureVideoStop()
        }
    }

    private fun setRecordLabel(resId: Int) {
        activity?.runOnUiThread { recordButton?.setText(resId) }
    }

    // ---------- USB audio ----------

    private fun toggleAudio() {
        val monitor = audioMonitor ?: return
        if (monitor.isRunning) {
            monitor.stop()
            toast(getString(R.string.toast_audio_off))
        } else {
            when (monitor.start()) {
                AudioMonitor.StartResult.STARTED -> toast(getString(R.string.toast_audio_on))
                AudioMonitor.StartResult.NO_USB_AUDIO -> toast(getString(R.string.toast_no_usb_audio))
                AudioMonitor.StartResult.NO_PERMISSION -> toast(getString(R.string.toast_no_mic_permission))
                AudioMonitor.StartResult.FAILED -> toast(getString(R.string.toast_audio_failed))
            }
        }
        refreshStatus()
    }

    // ---------- video size ----------

    private fun chooseSize() {
        scheduleHide()
        if (!isCameraOpened()) {
            toast(getString(R.string.toast_no_camera))
            return
        }
        val sizes = getAllPreviewSizes()
            ?.toList()
            ?.sortedByDescending { it.width * it.height }
            .orEmpty()
        if (sizes.isEmpty()) {
            toast(getString(R.string.toast_no_sizes))
            return
        }
        val labels = sizes.map { "${it.width} x ${it.height}" }.toTypedArray()
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.dialog_size_title)
            .setItems(labels) { _, index ->
                val chosen = sizes[index]
                updateResolution(chosen.width, chosen.height)
                toast(getString(R.string.toast_size_changed, labels[index]))
            }
            .show()
    }

    // ---------- helpers / lifecycle ----------

    private fun toast(message: String) {
        activity?.runOnUiThread {
            context?.let { Toast.makeText(it, message, Toast.LENGTH_SHORT).show() }
        }
    }

    override fun onStop() {
        // Don't keep playing audio when the app is not on screen.
        audioMonitor?.stop()
        super.onStop()
    }

    override fun clear() {
        contentRoot?.removeCallbacks(ticker)
        contentRoot?.removeCallbacks(hideControlsRunnable)
        audioMonitor?.stop()
        audioMonitor = null
        super.clear()
        hintText = null
        statusText = null
        controlBar = null
        recordButton = null
        audioButton = null
        contentRoot = null
    }
}
