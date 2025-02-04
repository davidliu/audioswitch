package com.twilio.audioswitch

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.AudioManager.OnAudioFocusChangeListener
import android.os.Build
import androidx.annotation.RequiresApi
import com.twilio.audioswitch.android.BuildWrapper
import com.twilio.audioswitch.android.Logger

private const val TAG = "AudioDeviceManager"

internal class AudioDeviceManager(
    private val context: Context,
    private val logger: Logger,
    private val audioManager: AudioManager,
    private val build: BuildWrapper = BuildWrapper(),
    private val audioFocusRequest: AudioFocusRequestWrapper = AudioFocusRequestWrapper(),
    private val audioFocusChangeListener: OnAudioFocusChangeListener
) {

    private var savedAudioMode = 0
    private var savedIsMicrophoneMuted = false
    private var savedSpeakerphoneEnabled = false
    private var audioRequest: AudioFocusRequest? = null

    var audioMode = AudioManager.MODE_IN_COMMUNICATION
    var focusMode = AudioManager.AUDIOFOCUS_GAIN_TRANSIENT
    var audioStreamType = AudioManager.STREAM_VOICE_CALL
    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    var audioAttributeUsageType = AudioAttributes.USAGE_VOICE_COMMUNICATION
    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    var audioAttributeContentType = AudioAttributes.CONTENT_TYPE_SPEECH

    fun hasEarpiece(): Boolean {
        val hasEarpiece = context.packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)
        if (hasEarpiece) {
            logger.d(TAG, "Earpiece available")
        }
        return hasEarpiece
    }

    @SuppressLint("NewApi")
    fun hasSpeakerphone(): Boolean {
        return if (
            build.getVersion() >= Build.VERSION_CODES.M &&
            context.packageManager.hasSystemFeature(PackageManager.FEATURE_AUDIO_OUTPUT)
        ) {
            val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            for (device in devices) {
                if (device.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER) {
                    logger.d(TAG, "Speakerphone available")
                    return true
                }
            }
            false
        } else {
            logger.d(TAG, "Speakerphone available")
            true
        }
    }

    @SuppressLint("NewApi")
    fun setAudioFocus() {
        // Request audio focus before making any device switch.
        if (build.getVersion() >= Build.VERSION_CODES.O) {
            audioRequest = audioFocusRequest.buildRequest(audioFocusChangeListener, focusMode, audioAttributeUsageType, audioAttributeContentType)
            audioRequest?.let { audioManager.requestAudioFocus(it) }
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(
                audioFocusChangeListener,
                audioStreamType,
                focusMode
            )
        }
        /*
         * Start by setting MODE_IN_COMMUNICATION as default audio mode. It is
         * required to be in this mode when playout and/or recording starts for
         * best possible VoIP performance. Some devices have difficulties with speaker mode
         * if this is not set.
         */
        audioManager.mode = audioMode
    }

    fun enableBluetoothSco(enable: Boolean) {
        audioManager.run { if (enable) startBluetoothSco() else stopBluetoothSco() }
    }

    fun enableSpeakerphone(enable: Boolean) {
        audioManager.isSpeakerphoneOn = enable
    }

    fun mute(mute: Boolean) {
        audioManager.isMicrophoneMute = mute
    }

    // TODO Consider persisting audio state in the event of process death
    fun cacheAudioState() {
        savedAudioMode = audioManager.mode
        savedIsMicrophoneMuted = audioManager.isMicrophoneMute
        savedSpeakerphoneEnabled = audioManager.isSpeakerphoneOn
    }

    @SuppressLint("NewApi")
    fun restoreAudioState() {
        audioManager.mode = savedAudioMode
        mute(savedIsMicrophoneMuted)
        enableSpeakerphone(savedSpeakerphoneEnabled)
        if (build.getVersion() >= Build.VERSION_CODES.O) {
            audioRequest?.let { audioManager.abandonAudioFocusRequest(it) }
            audioRequest = null
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(audioFocusChangeListener)
        }
    }
}
