package com.example.usbcamviewer

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import com.jiangdg.ausbc.base.CameraFragment
import com.jiangdg.ausbc.callback.ICaptureCallBack
import com.jiangdg.ausbc.camera.bean.CameraRequest
import com.jiangdg.ausbc.widget.AspectRatioTextureView
import com.jiangdg.ausbc.widget.IAspectRatio
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Shows the live preview of a UVC (USB Video Class) camera and lets the user
 * take photos and record video. Everything is saved to the app's own folder:
 * Android/data/com.example.usbcamviewer/files/ (no storage permission needed).
 */
class CameraViewerFragment : CameraFragment() {

    private var rootView: View? = null
    private var hintText: TextView? = null
    private var recordButton: Button? = null
    private var recording = false

    override fun getRootView(inflater: LayoutInflater, container: ViewGroup?): View? {
        rootView = inflater.inflate(R.layout.fragment_camera, container, false)
        return rootView
    }

    override fun getCameraView(): IAspectRatio? = AspectRatioTextureView(requireContext())

    override fun getCameraViewContainer(): ViewGroup? =
        rootView?.findViewById(R.id.cameraContainer)

    override fun getCameraRequest(): CameraRequest =
        CameraRequest.Builder()
            .setPreviewWidth(1280)
            .setPreviewHeight(720)
            .create()

    override fun initView() {
        super.initView()
        val root = rootView ?: return
        hintText = root.findViewById(R.id.hintText)
        recordButton = root.findViewById(R.id.btnRecord)

        root.findViewById<Button>(R.id.btnSnapshot).setOnClickListener { takePhoto() }
        recordButton?.setOnClickListener { toggleRecording() }
    }

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
                    recordButton?.setText(R.string.btn_record_stop)
                }
                override fun onError(error: String?) {
                    recording = false
                    recordButton?.setText(R.string.btn_record_start)
                    toast(getString(R.string.toast_error, error ?: "unknown"))
                }
                override fun onComplete(path: String?) {
                    recording = false
                    recordButton?.setText(R.string.btn_record_start)
                    toast(getString(R.string.toast_saved, path ?: ""))
                }
            }, path)
        } else {
            captureVideoStop()
        }
    }

    private fun toast(message: String) {
        activity?.runOnUiThread {
            Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        hintText = null
        recordButton = null
        super.onDestroyView()
    }
}
