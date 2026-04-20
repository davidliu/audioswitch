package com.twilio.audioswitch

import android.content.Context
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.annotation.RequiresApi
import androidx.annotation.VisibleForTesting
import com.twilio.audioswitch.android.Logger
import com.twilio.audioswitch.android.ProductionLogger
import com.twilio.audioswitch.scanners.CommunicationDeviceScanner
import com.twilio.audioswitch.scanners.Scanner

/**
 * Like [AudioSwitch], but routes playout using [AudioManager.setCommunicationDevice] and
 * discovers eligible devices via [AudioManager.getAvailableCommunicationDevices], instead of
 * toggling speakerphone and Bluetooth SCO.
 *
 * Requires Android 12 (API 31) or newer.
 */
@RequiresApi(Build.VERSION_CODES.S)
class CommDeviceAudioSwitch : AbstractAudioSwitch {

    private val communicationDeviceScanner: CommunicationDeviceScanner
        get() = deviceScanner as CommunicationDeviceScanner

    @JvmOverloads
    constructor(
        context: Context,
        loggingEnabled: Boolean = false,
        audioFocusChangeListener: AudioManager.OnAudioFocusChangeListener = AudioManager.OnAudioFocusChangeListener {},
        preferredDeviceList: List<Class<out AudioDevice>> = defaultPreferredDeviceList
    ) : this(
        context,
        audioFocusChangeListener,
        ProductionLogger(loggingEnabled),
        preferredDeviceList
    )

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    internal constructor(
        context: Context,
        audioFocusChangeListener: AudioManager.OnAudioFocusChangeListener,
        logger: Logger,
        preferredDeviceList: List<Class<out AudioDevice>>,
        audioManager: AudioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager,
        audioDeviceManager: AudioDeviceManager = AudioDeviceManager(
            context,
            logger,
            audioManager,
            audioFocusChangeListener = audioFocusChangeListener
        ),
        handler: Handler = Handler(Looper.getMainLooper()),
        scanner: Scanner = CommunicationDeviceScanner(audioManager, handler),
    ) : super(
        context = context,
        audioFocusChangeListener = audioFocusChangeListener,
        scanner = scanner,
        logger = logger,
        preferredDeviceList = preferredDeviceList,
        audioDeviceManager = audioDeviceManager,
    ) {
        require(scanner is CommunicationDeviceScanner) {
            "CommDeviceAudioSwitch requires a CommunicationDeviceScanner"
        }
    }

    /**
     * Mirrors [CommunicationDeviceScanner] connect events: each [audioDevice] reported by the
     * scanner is added to [availableUniqueAudioDevices] with no wired/earpiece suppression.
     */
    override fun onDeviceConnected(audioDevice: AudioDevice) {
        this.logger.d(TAG_AUDIO_SWITCH, "onDeviceConnected($audioDevice)")
        val wasAdded = this.availableUniqueAudioDevices.add(audioDevice)
        this.selectAudioDevice(wasListChanged = wasAdded)
    }

    /**
     * Mirrors [CommunicationDeviceScanner] disconnect events: removes [audioDevice] from
     * [availableUniqueAudioDevices] and clears a matching user selection.
     */
    override fun onDeviceDisconnected(audioDevice: AudioDevice) {
        this.logger.d(TAG_AUDIO_SWITCH, "onDeviceDisconnected($audioDevice)")
        val wasChanged = this.availableUniqueAudioDevices.remove(audioDevice)
        if (this.userSelectedAudioDevice == audioDevice) {
            this.userSelectedAudioDevice = null
        }
        this.selectAudioDevice(wasChanged)
    }

    override fun onActivate(audioDevice: AudioDevice) {
        this.logger.d(TAG_AUDIO_SWITCH, "onActivate($audioDevice)")
        val deviceInfo = communicationDeviceScanner.findCommunicationDeviceInfo(audioDevice)
        if (deviceInfo != null) {
            val success = audioDeviceManager.setCommunicationDevice(deviceInfo)
            if (!success) {
                this.logger.d(TAG_AUDIO_SWITCH, "setCommunicationDevice returned false for $audioDevice")
            }
        } else {
            this.logger.d(TAG_AUDIO_SWITCH, "No communication device info for $audioDevice")
        }
    }

    override fun onDeactivate() {
        this.logger.d(TAG_AUDIO_SWITCH, "onDeactivate")
        audioDeviceManager.clearCommunicationDevice()
    }
}
