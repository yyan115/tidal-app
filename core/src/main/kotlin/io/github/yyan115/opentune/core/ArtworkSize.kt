package io.github.yyan115.opentune.core

/** The sizes of the covers that TIDAL serves. A cover is requested at 640 pixels, and the full player asks for a larger copy. */
object ArtworkSize {
    private const val HOST = "https://resources.tidal.com/images/"
    private const val STANDARD = "/640x640.jpg"
    private const val LARGE = "/1280x1280.jpg"

    /** The address of the larger copy of a TIDAL cover. Any other address, such as a file on the device, stays as it is. */
    fun large(address: String?): String? =
        if (address != null && address.startsWith(HOST) && address.endsWith(STANDARD)) address.removeSuffix(STANDARD) + LARGE else address
}
