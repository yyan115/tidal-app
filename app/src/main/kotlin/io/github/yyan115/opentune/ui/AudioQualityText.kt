package io.github.yyan115.opentune.ui

import androidx.annotation.StringRes
import io.github.yyan115.opentune.R
import io.github.yyan115.opentune.core.AudioQuality

/** The name of a quality level in the language of the device. */
@StringRes
fun AudioQuality.labelRes(): Int = when (this) {
    AudioQuality.HI_RES_LOSSLESS -> R.string.quality_max
    AudioQuality.LOSSLESS -> R.string.quality_lossless
    AudioQuality.HIGH -> R.string.quality_high
    AudioQuality.LOW -> R.string.quality_low
}
