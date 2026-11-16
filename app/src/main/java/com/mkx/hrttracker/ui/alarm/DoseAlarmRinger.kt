package com.mkx.hrttracker.ui.alarm

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * Rings the alarm tone and vibrates for the full-screen dose alarm, each on a
 * loop, until [stop] or [RING_TIMEOUT_MS] — the cap keeps an unattended phone
 * from ringing until its battery runs out. The tone plays on the alarm stream,
 * so it follows the alarm volume rather than the ringer's silent mode.
 */
internal class DoseAlarmRinger(private val context: Context) {
    private var player: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private val handler = Handler(Looper.getMainLooper())
    private val timeout = Runnable { stop() }

    /**
     * [soundUri] is the sound picked for persistent reminders, null for the
     * device's alarm tone. [continuousVibration] false buzzes three times only.
     */
    fun start(
        sound: Boolean,
        vibration: Boolean,
        soundUri: Uri? = null,
        continuousVibration: Boolean = true,
    ) {
        stop()
        if (sound) startSound(soundUri)
        if (vibration) startVibration(continuousVibration)
        if (sound || vibration) {
            handler.postDelayed(timeout, RING_TIMEOUT_MS)
        }
    }

    fun stop() {
        handler.removeCallbacks(timeout)
        player?.let { mediaPlayer ->
            runCatching { mediaPlayer.stop() }
            mediaPlayer.release()
        }
        player = null
        vibrator?.cancel()
        vibrator = null
    }

    private fun startSound(soundUri: Uri?) {
        // A picked sound that can no longer be read (deleted, permission gone)
        // falls back to the alarm tone rather than leaving the alarm silent.
        player = soundUri?.let(::loopingPlayer) ?: alarmToneUri()?.let(::loopingPlayer)
    }

    private fun loopingPlayer(uri: Uri): MediaPlayer? {
        return runCatching {
            MediaPlayer().apply {
                setAudioAttributes(alarmAudioAttributes)
                setDataSource(context, uri)
                isLooping = true
                prepare()
                start()
            }
        }.getOrNull()
    }

    @Suppress("DEPRECATION")
    private fun startVibration(continuous: Boolean) {
        val resolved = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            context.getSystemService(Vibrator::class.java)
        }
        if (resolved == null || !resolved.hasVibrator()) {
            return
        }
        // Alarm usage keeps vibrating where Do Not Disturb lets alarms through.
        val effect = if (continuous) {
            VibrationEffect.createWaveform(VIBRATION_PATTERN_MS, 0)
        } else {
            VibrationEffect.createWaveform(THREE_PULSES_PATTERN_MS, -1)
        }
        resolved.vibrate(effect, alarmAudioAttributes)
        vibrator = resolved
    }

    /** The user's alarm tone, else their notification sound, else their ringtone. */
    private fun alarmToneUri(): Uri? {
        return listOf(
            RingtoneManager.TYPE_ALARM,
            RingtoneManager.TYPE_NOTIFICATION,
            RingtoneManager.TYPE_RINGTONE,
        ).firstNotNullOfOrNull { type ->
            runCatching { RingtoneManager.getActualDefaultRingtoneUri(context, type) }.getOrNull()
        }
    }

    private companion object {
        val alarmAudioAttributes: AudioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        // Off, buzz, pause; repeated from the start.
        val VIBRATION_PATTERN_MS = longArrayOf(0L, 800L, 700L)

        // Off, then three buzzes with pauses between; played once.
        val THREE_PULSES_PATTERN_MS = longArrayOf(0L, 500L, 400L, 500L, 400L, 500L)
        const val RING_TIMEOUT_MS = 60_000L
    }
}
