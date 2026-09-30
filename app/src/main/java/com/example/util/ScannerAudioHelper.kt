package com.example.util

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import android.media.ToneGenerator
import android.os.SystemClock
import android.util.Log
import com.example.R

class ScannerAudioHelper(private val context: Context) {

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    private var soundPool: SoundPool? = null
    private var soundId: Int = 0
    private var isSoundLoaded: Boolean = false

    private var toneGenerator: ToneGenerator? = null

    private var lastPlayTimestamp: Long = 0L

    init {
        try {
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()

            val sp = SoundPool.Builder()
                .setMaxStreams(2)
                .setAudioAttributes(audioAttributes)
                .build()

            sp.setOnLoadCompleteListener { _, sampleId, status ->
                if (status == 0 && sampleId == soundId) {
                    isSoundLoaded = true
                }
            }

            soundId = sp.load(context, R.raw.scan_beep, 1)
            soundPool = sp
        } catch (e: Exception) {
            Log.w("ScannerAudioHelper", "SoundPool init failed, using ToneGenerator fallback", e)
        }

        try {
            toneGenerator = ToneGenerator(AudioManager.STREAM_MUSIC, 95)
        } catch (e: Exception) {
            Log.w("ScannerAudioHelper", "ToneGenerator init failed", e)
        }
    }

    /**
     * Plays ONE short confirmation beep/tick.
     * Respects Silent/Vibrate/DND modes (does not force sound).
     * Prevents duplicate rapid triggers within 400ms.
     */
    fun playConfirmationBeep() {
        val now = SystemClock.uptimeMillis()
        if (now - lastPlayTimestamp < 400L) {
            return
        }
        lastPlayTimestamp = now

        // Respect Silent/Vibrate modes
        val ringerMode = audioManager?.ringerMode ?: AudioManager.RINGER_MODE_NORMAL
        if (ringerMode != AudioManager.RINGER_MODE_NORMAL) {
            return
        }

        var played = false
        if (isSoundLoaded && soundPool != null && soundId != 0) {
            try {
                val streamId = soundPool?.play(soundId, 1.0f, 1.0f, 1, 0, 1.0f) ?: 0
                if (streamId > 0) {
                    played = true
                }
            } catch (e: Exception) {
                Log.w("ScannerAudioHelper", "SoundPool play failed", e)
            }
        }

        if (!played) {
            try {
                toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP, 120)
            } catch (e: Exception) {
                Log.w("ScannerAudioHelper", "ToneGenerator play failed", e)
            }
        }
    }

    fun release() {
        try {
            soundPool?.release()
            soundPool = null
        } catch (_: Exception) {}

        try {
            toneGenerator?.release()
            toneGenerator = null
        } catch (_: Exception) {}
    }
}
