package io.github.yyan115.opentune.playback

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.audiofx.Equalizer
import android.os.Build

/** An audio output that Android can play to. */
data class OutputDevice(val name: String, val kind: String, val sampleRates: List<Int>, val active: Boolean)

object AudioOutput {
    private fun kind(type: Int): String? = when (type) {
        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "Phone speaker"
        AudioDeviceInfo.TYPE_WIRED_HEADPHONES, AudioDeviceInfo.TYPE_WIRED_HEADSET -> "Wired headphones"
        AudioDeviceInfo.TYPE_USB_DEVICE, AudioDeviceInfo.TYPE_USB_HEADSET, AudioDeviceInfo.TYPE_USB_ACCESSORY -> "USB audio"
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> "Bluetooth"
        AudioDeviceInfo.TYPE_BLE_HEADSET, AudioDeviceInfo.TYPE_BLE_SPEAKER, AudioDeviceInfo.TYPE_BLE_BROADCAST -> "Bluetooth LE"
        AudioDeviceInfo.TYPE_HDMI, AudioDeviceInfo.TYPE_HDMI_ARC, AudioDeviceInfo.TYPE_HDMI_EARC -> "HDMI"
        AudioDeviceInfo.TYPE_LINE_ANALOG, AudioDeviceInfo.TYPE_LINE_DIGITAL -> "Line out"
        AudioDeviceInfo.TYPE_DOCK -> "Dock"
        else -> null
    }

    /** The outputs that music uses now, followed by the other connected outputs. */
    fun devices(context: Context): List<OutputDevice> {
        val manager = context.getSystemService(AudioManager::class.java)
        val routed = if (Build.VERSION.SDK_INT >= 33) runCatching {
            manager.getAudioDevicesForAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()).map { it.id }
        }.getOrDefault(emptyList()) else emptyList()
        return manager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).mapNotNull { device ->
            kind(device.type)?.let { label ->
                OutputDevice(device.productName?.toString().orEmpty().ifBlank { label }, label,
                    device.sampleRates.filter { it > 0 }.sorted(), active = device.id in routed)
            }
        }.sortedByDescending { it.active }
    }
}

/** The bands, range and presets of the built-in equalizer, or null when the device has none. */
data class EqualizerInfo(val minLevel: Int, val maxLevel: Int, val centerHz: List<Int>, val presets: List<Preset>) {
    data class Preset(val name: String, val levels: List<Int>)
    val bands get() = centerHz.size

    companion object {
        fun read(): EqualizerInfo? = runCatching {
            val effect = Equalizer(0, 0)
            try {
                val bands = effect.numberOfBands.toInt()
                val presets = (0 until effect.numberOfPresets).map { index ->
                    effect.usePreset(index.toShort())
                    Preset(effect.getPresetName(index.toShort()), (0 until bands).map { effect.getBandLevel(it.toShort()).toInt() })
                }
                EqualizerInfo(effect.bandLevelRange[0].toInt(), effect.bandLevelRange[1].toInt(),
                    (0 until bands).map { effect.getCenterFreq(it.toShort()) / 1000 }, presets)
            } finally { effect.release() }
        }.getOrNull()
    }
}
