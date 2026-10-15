package com.example.usbcamviewer

import android.app.Application
import android.os.Build
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

/**
 * Saves the details of any crash to a file so MainActivity can show them on screen the
 * next time the app is opened. This makes it possible to see why the app closed.
 */
class App : Application() {

    override fun onCreate() {
        super.onCreate()
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val trace = StringWriter().also { throwable.printStackTrace(PrintWriter(it)) }.toString()
                val report = "Thread: ${thread.name}\n" +
                    "Phone: ${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE} " +
                    "(API ${Build.VERSION.SDK_INT}), ABIs: ${Build.SUPPORTED_ABIS.joinToString()}\n\n" +
                    trace
                File(filesDir, CRASH_FILE).writeText(report)
            } catch (_: Throwable) {
                // never let the reporter itself cause trouble
            }
            previous?.uncaughtException(thread, throwable)
        }
    }

    companion object {
        const val CRASH_FILE = "last_crash.txt"
    }
}
