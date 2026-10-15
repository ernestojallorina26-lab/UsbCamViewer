package com.example.usbcamviewer

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import androidx.core.content.ContextCompat

/**
 * Plays the audio coming from a USB capture card (for example the game sound of an
 * Xbox 360 / PSP) live through the phone's speaker or headphones.
 *
 * It only starts when a USB audio input is really present. Without that check the
 * phone's own microphone would pick up the speaker and cause loud feedback.
 */
class AudioMonitor(private val context: Context) {

    enum class StartResult { STARTED, NO_USB_AUDIO, NO_PERMISSION, FAILED }

    @Volatile
    var isRunning: Boolean = false
        private set

    private var worker: Thread? = null

    fun findUsbInput(): AudioDeviceInfo? {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        return audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS).firstOrNull {
            it.type == AudioDeviceInfo.TYPE_USB_DEVICE || it.type == AudioDeviceInfo.TYPE_USB_HEADSET
        }
    }

    /**
     * Where the sound should come out. If headphones (wired or Bluetooth) are connected we let
     * Android use them; otherwise we force the phone's own speaker so the sound never gets
     * routed back into the USB capture device.
     */
    private fun pickOutput(): AudioDeviceInfo? {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val outputs = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        val headphones = outputs.any {
            it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP
        }
        if (headphones) return null
        return outputs.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
    }

    fun start(): StartResult {
        if (isRunning) return StartResult.STARTED
        val granted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) return StartResult.NO_PERMISSION

        val usb = findUsbInput() ?: return StartResult.NO_USB_AUDIO

        for (rate in intArrayOf(48000, 44100)) {
            for (stereo in booleanArrayOf(true, false)) {
                if (tryStart(usb, rate, stereo)) return StartResult.STARTED
            }
        }
        return StartResult.FAILED
    }

    @SuppressLint("MissingPermission")
    private fun tryStart(usb: AudioDeviceInfo, rate: Int, stereo: Boolean): Boolean {
        val inMask = if (stereo) AudioFormat.CHANNEL_IN_STEREO else AudioFormat.CHANNEL_IN_MONO
        val outMask = if (stereo) AudioFormat.CHANNEL_OUT_STEREO else AudioFormat.CHANNEL_OUT_MONO
        val minIn = AudioRecord.getMinBufferSize(rate, inMask, AudioFormat.ENCODING_PCM_16BIT)
        val minOut = AudioTrack.getMinBufferSize(rate, outMask, AudioFormat.ENCODING_PCM_16BIT)
        if (minIn <= 0 || minOut <= 0) return false

        var recorder: AudioRecord? = null
        var player: AudioTrack? = null
        try {
            recorder = AudioRecord.Builder()
                .setAudioSource(MediaRecorder.AudioSource.MIC)
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(rate)
                        .setChannelMask(inMask)
                        .build()
                )
                .setBufferSizeInBytes(minIn * 2)
                .build()
            if (recorder.state != AudioRecord.STATE_INITIALIZED) {
                recorder.release()
                return false
            }
            // Record from the USB device instead of the phone microphone.
            recorder.setPreferredDevice(usb)

            player = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(rate)
                        .setChannelMask(outMask)
                        .build()
                )
                .setBufferSizeInBytes(minOut * 2)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
                .build()

            // Play through the phone speaker (or headphones if connected).
            val output = pickOutput()
            if (output != null) player.setPreferredDevice(output)

            recorder.startRecording()
            player.play()
            isRunning = true

            val rec = recorder
            val trk = player
            val bufferSize = minIn
            worker = Thread { loop(rec, trk, bufferSize) }.apply {
                priority = Thread.MAX_PRIORITY
                start()
            }
            return true
        } catch (e: Exception) {
            isRunning = false
            try { recorder?.release() } catch (_: Exception) {}
            try { player?.release() } catch (_: Exception) {}
            return false
        }
    }

    private fun loop(recorder: AudioRecord, player: AudioTrack, bufferSize: Int) {
        val buffer = ByteArray(bufferSize)
        try {
            while (isRunning) {
                val n = recorder.read(buffer, 0, buffer.size)
                if (n > 0) {
                    player.write(buffer, 0, n)
                } else if (n < 0) {
                    break // device unplugged or error
                }
            }
        } catch (_: Exception) {
            // fall through to cleanup
        } finally {
            isRunning = false
            try { recorder.stop() } catch (_: Exception) {}
            recorder.release()
            try { player.stop() } catch (_: Exception) {}
            player.release()
        }
    }

    fun stop() {
        isRunning = false
        try {
            worker?.join(800)
        } catch (_: InterruptedException) {
        }
        worker = null
    }
}
