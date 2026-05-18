package com.twilio.audioswitch

import android.media.AudioDeviceInfo
import android.media.AudioManager
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import com.twilio.audioswitch.scanners.CommunicationDeviceScanner
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Before
import org.junit.Test

class CommDeviceAudioSwitchTest : BaseTest() {

    @Before
    fun stubCommunicationDevices() {
        whenever(audioManager.getAvailableCommunicationDevices()).thenReturn(emptyList())
    }

    @Test
    fun `start should register audio device callback and communication device listener`() {
        val audioSwitch = getCommDeviceAudioSwitch()
        val scanner = audioSwitch.deviceScanner as CommunicationDeviceScanner

        audioSwitch.start(audioDeviceChangeListener)

        assertThat(scanner.listener, equalTo(audioSwitch))
        verify(audioManager).registerAudioDeviceCallback(scanner, handler)
        verify(audioManager).addOnCommunicationDeviceChangedListener(any(), any())
    }

    @Test
    fun `stop before start should not unregister listeners`() {
        val audioSwitch = getCommDeviceAudioSwitch()
        val scanner = audioSwitch.deviceScanner as CommunicationDeviceScanner

        audioSwitch.stop()

        verify(audioManager, never()).removeOnCommunicationDeviceChangedListener(any())
        verify(audioManager, never()).unregisterAudioDeviceCallback(scanner)
    }

    @Test
    fun `stop should unregister audio device callback and communication device listener`() {
        val audioSwitch = getCommDeviceAudioSwitch()
        val scanner = audioSwitch.deviceScanner as CommunicationDeviceScanner

        audioSwitch.start(audioDeviceChangeListener)
        audioSwitch.stop()

        assertThat(audioSwitch.audioDeviceChangeListener, equalTo(null))
        verify(audioManager).removeOnCommunicationDeviceChangedListener(any())
        verify(audioManager).unregisterAudioDeviceCallback(scanner)
    }

    @Test
    fun `activate should route using setCommunicationDevice`() {
        val wiredInfo = mock<AudioDeviceInfo> {
            whenever(mock.id).thenReturn(42)
            whenever(mock.type).thenReturn(AudioDeviceInfo.TYPE_WIRED_HEADSET)
            whenever(mock.productName).thenReturn("Headset")
        }
        whenever(audioManager.getAvailableCommunicationDevices()).thenReturn(listOf(wiredInfo))
        whenever(audioManager.setCommunicationDevice(wiredInfo)).thenReturn(true)

        val audioSwitch = getCommDeviceAudioSwitch()
        audioSwitch.start(audioDeviceChangeListener)
        audioSwitch.onDeviceConnected(AudioDevice.WiredHeadset())
        audioSwitch.selectDevice(AudioDevice.WiredHeadset())
        audioSwitch.activate()

        verify(audioManager).setCommunicationDevice(wiredInfo)
    }

    @Test
    fun `deactivate should clear communication device`() {
        val audioSwitch = getCommDeviceAudioSwitch()
        audioSwitch.start(audioDeviceChangeListener)
        audioSwitch.activate()
        audioSwitch.deactivate()

        verify(audioManager).clearCommunicationDevice()
    }
}
