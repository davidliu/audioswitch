package com.twilio.audioswitch.scanners

import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import com.twilio.audioswitch.AudioDevice
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.CoreMatchers.nullValue
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.junit.MockitoJUnitRunner

@RunWith(MockitoJUnitRunner.Silent::class)
class CommunicationDeviceScannerTest {

    private val audioManager = mock<AudioManager>()
    private val handler = mock<Handler> {
        whenever(mock.post(any())).thenAnswer { invocation ->
            (invocation.arguments[0] as Runnable).run()
            true
        }
    }

    private lateinit var scanner: CommunicationDeviceScanner
    private val connected = mutableListOf<AudioDevice>()
    private val disconnected = mutableListOf<AudioDevice>()

    private val listener = object : Scanner.Listener {
        override fun onDeviceConnected(audioDevice: AudioDevice) {
            connected.add(audioDevice)
        }

        override fun onDeviceDisconnected(audioDevice: AudioDevice) {
            disconnected.add(audioDevice)
        }
    }

    @Before
    fun setUp() {
        whenever(audioManager.getAvailableCommunicationDevices()).thenReturn(emptyList())
        scanner = CommunicationDeviceScanner(audioManager, handler)
        connected.clear()
        disconnected.clear()
    }

    @Test
    fun `start registers audio device callback and communication device listener`() {
        scanner.start(listener)

        verify(audioManager).registerAudioDeviceCallback(scanner, handler)
        verify(audioManager).addOnCommunicationDeviceChangedListener(any(), any())
        assertThat(scanner.listener, equalTo(listener))
    }

    @Test
    fun `stop unregisters listeners and clears scanner listener`() {
        scanner.start(listener)
        scanner.stop()

        verify(audioManager).removeOnCommunicationDeviceChangedListener(any())
        verify(audioManager).unregisterAudioDeviceCallback(scanner)
        assertThat(scanner.listener, nullValue())
    }

    @Test
    fun `isDeviceActive returns true when device appears in available communication devices`() {
        val earpieceInfo = mock<AudioDeviceInfo> {
            whenever(mock.id).thenReturn(1)
            whenever(mock.type).thenReturn(AudioDeviceInfo.TYPE_BUILTIN_EARPIECE)
            whenever(mock.productName).thenReturn("Earpiece")
        }
        whenever(audioManager.getAvailableCommunicationDevices()).thenReturn(listOf(earpieceInfo))

        assertTrue(scanner.isDeviceActive(AudioDevice.Earpiece()))
    }

    @Test
    fun `isDeviceActive returns false when device is not in available communication devices`() {
        whenever(audioManager.getAvailableCommunicationDevices()).thenReturn(emptyList())

        assertFalse(scanner.isDeviceActive(AudioDevice.Earpiece()))
    }

    @Test
    fun `findCommunicationDeviceInfo returns first matching AudioDeviceInfo`() {
        val wired = mock<AudioDeviceInfo> {
            whenever(mock.id).thenReturn(10)
            whenever(mock.type).thenReturn(AudioDeviceInfo.TYPE_WIRED_HEADSET)
            whenever(mock.productName).thenReturn("HS")
        }
        whenever(audioManager.getAvailableCommunicationDevices()).thenReturn(listOf(wired))

        assertThat(scanner.findCommunicationDeviceInfo(AudioDevice.WiredHeadset()), equalTo(wired))
    }

    @Test
    fun `onAudioDevicesAdded notifies when communication device list gains an entry`() {
        val wired = mock<AudioDeviceInfo> {
            whenever(mock.id).thenReturn(2)
            whenever(mock.type).thenReturn(AudioDeviceInfo.TYPE_WIRED_HEADSET)
            whenever(mock.productName).thenReturn("Wired")
        }
        scanner.start(listener)
        connected.clear()

        whenever(audioManager.getAvailableCommunicationDevices()).thenReturn(listOf(wired))
        scanner.onAudioDevicesAdded(emptyArray())

        assertThat(connected, equalTo(listOf(AudioDevice.WiredHeadset())))
        assertThat(disconnected, equalTo(emptyList()))
    }

    @Test
    fun `onAudioDevicesRemoved notifies when communication device list loses an entry`() {
        val wired = mock<AudioDeviceInfo> {
            whenever(mock.id).thenReturn(3)
            whenever(mock.type).thenReturn(AudioDeviceInfo.TYPE_WIRED_HEADSET)
            whenever(mock.productName).thenReturn("Wired")
        }
        whenever(audioManager.getAvailableCommunicationDevices()).thenReturn(listOf(wired))
        scanner.start(listener)
        connected.clear()
        disconnected.clear()

        whenever(audioManager.getAvailableCommunicationDevices()).thenReturn(emptyList())
        scanner.onAudioDevicesRemoved(emptyArray())

        assertThat(disconnected, equalTo(listOf(AudioDevice.WiredHeadset())))
        assertThat(connected, equalTo(emptyList()))
    }

    @Test
    fun `communication device changed listener triggers same refresh as audio callback`() {
        val wired = mock<AudioDeviceInfo> {
            whenever(mock.id).thenReturn(4)
            whenever(mock.type).thenReturn(AudioDeviceInfo.TYPE_WIRED_HEADSET)
            whenever(mock.productName).thenReturn("Wired")
        }
        scanner.start(listener)
        connected.clear()

        val routingCaptor = argumentCaptor<AudioManager.OnCommunicationDeviceChangedListener>()
        verify(audioManager).addOnCommunicationDeviceChangedListener(any(), routingCaptor.capture())

        whenever(audioManager.getAvailableCommunicationDevices()).thenReturn(listOf(wired))
        routingCaptor.firstValue.onCommunicationDeviceChanged(wired)

        assertThat(connected, equalTo(listOf(AudioDevice.WiredHeadset())))
    }

    @Test
    fun `toTwilioAudioDevice maps wired headset`() {
        val info = mock<AudioDeviceInfo> {
            whenever(mock.type).thenReturn(AudioDeviceInfo.TYPE_WIRED_HEADSET)
            whenever(mock.productName).thenReturn("X")
        }
        assertThat(info.toTwilioAudioDevice(), equalTo(AudioDevice.WiredHeadset()))
    }

    @Test
    fun `toTwilioAudioDevice maps bluetooth sco using product name`() {
        val info = mock<AudioDeviceInfo> {
            whenever(mock.type).thenReturn(AudioDeviceInfo.TYPE_BLUETOOTH_SCO)
            whenever(mock.productName).thenReturn("My Car")
        }
        assertThat(info.toTwilioAudioDevice(), equalTo(AudioDevice.BluetoothHeadset("My Car")))
    }

    @Test
    fun `toTwilioAudioDevice maps earpiece and speakerphone`() {
        val ear = mock<AudioDeviceInfo> {
            whenever(mock.type).thenReturn(AudioDeviceInfo.TYPE_BUILTIN_EARPIECE)
            whenever(mock.productName).thenReturn("Earpiece")
        }
        val spk = mock<AudioDeviceInfo> {
            whenever(mock.type).thenReturn(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER)
            whenever(mock.productName).thenReturn("Speaker")
        }
        assertThat(ear.toTwilioAudioDevice(), equalTo(AudioDevice.Earpiece()))
        assertThat(spk.toTwilioAudioDevice(), equalTo(AudioDevice.Speakerphone()))
    }

    @Test
    fun `toTwilioAudioDevice returns null for unmapped output type`() {
        val info = mock<AudioDeviceInfo> {
            whenever(mock.type).thenReturn(AudioDeviceInfo.TYPE_HDMI)
            whenever(mock.productName).thenReturn("HDMI")
        }
        assertThat(info.toTwilioAudioDevice(), nullValue())
    }

    @Test
    fun `toTwilioAudioDevice maps ble headset on S plus`() {
        org.junit.Assume.assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)

        val info = mock<AudioDeviceInfo> {
            whenever(mock.type).thenReturn(AudioDeviceInfo.TYPE_BLE_HEADSET)
            whenever(mock.productName).thenReturn("LE buds")
        }
        assertThat(info.toTwilioAudioDevice(), equalTo(AudioDevice.BluetoothHeadset("LE buds")))
    }
}
