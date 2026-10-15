package com.example.usbcamviewer

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import java.io.File

class MainActivity : AppCompatActivity() {

    private val requiredPermissions = arrayOf(
        Manifest.permission.CAMERA,
        Manifest.permission.RECORD_AUDIO
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Monitor style: keep the screen on and draw edge to edge.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        if (Build.VERSION.SDK_INT >= 28) {
            val params = window.attributes
            params.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            window.attributes = params
        }

        setContentView(R.layout.activity_main)
        hideSystemBars()

        // If the app crashed last time, show the error on screen before doing anything else.
        val crashFile = File(filesDir, App.CRASH_FILE)
        if (crashFile.exists()) {
            showCrashReport(crashFile)
            return
        }

        if (hasAllPermissions()) {
            showCamera(savedInstanceState)
        } else {
            ActivityCompat.requestPermissions(this, requiredPermissions, REQUEST_PERMISSIONS)
        }
    }

    private fun showCrashReport(crashFile: File) {
        val report = try {
            crashFile.readText()
        } catch (e: Exception) {
            "Could not read the crash report: ${e.message}"
        }
        val text = TextView(this).apply {
            this.text = report
            setTextIsSelectable(true)
            setTextColor(Color.WHITE)
            textSize = 11f
            setPadding(32, 32, 32, 32)
        }
        val scroll = ScrollView(this).apply {
            setBackgroundColor(Color.BLACK)
            addView(text)
        }
        AlertDialog.Builder(this)
            .setTitle("The app closed because of an error")
            .setView(scroll)
            .setCancelable(false)
            .setPositiveButton("Continue") { _, _ ->
                crashFile.delete()
                recreate()
            }
            .show()
    }

    override fun onResume() {
        super.onResume()
        hideSystemBars()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    private fun hideSystemBars() {
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_PERMISSIONS) {
            // Show the viewer either way; audio is optional, camera permission is what USB cameras need.
            showCamera(null)
        }
    }

    private fun hasAllPermissions(): Boolean = requiredPermissions.all {
        ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
    }

    private fun showCamera(savedInstanceState: Bundle?) {
        if (savedInstanceState == null && supportFragmentManager.findFragmentByTag(TAG) == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragmentHost, CameraViewerFragment(), TAG)
                .commitAllowingStateLoss()
        }
    }

    companion object {
        private const val TAG = "camera_viewer"
        private const val REQUEST_PERMISSIONS = 100
    }
}
