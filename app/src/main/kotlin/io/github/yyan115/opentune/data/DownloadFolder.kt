package io.github.yyan115.opentune.data

import io.github.yyan115.opentune.core.DocumentKey

/** Where finished downloads are saved: the Music/OpenTune folder, or a folder that the listener picked. */
object DownloadFolder {
    /** The folder of downloads when the listener picks none. */
    const val DEFAULT = "Music/OpenTune"

    /** Whether the setting holds no picked folder. The setting is the address of the picked folder. */
    fun isDefault(setting: String) = setting.isBlank()

    /**
     * The folder as text for a screen. A folder on the main storage shows its path, and a folder on another volume shows the name of the volume first.
     * [other] names a folder of a provider that does not use paths.
     */
    fun summary(setting: String, internal: String, card: String, other: String = ""): String {
        if (isDefault(setting)) return DEFAULT
        val location = DocumentKey.treeLocation(setting) ?: return other
        val path = location.segments.joinToString("/")
        val volume = if (location.volume == "primary") internal else card
        return when {
            location.volume == "primary" && path.isNotEmpty() -> path
            path.isEmpty() -> volume
            else -> "$volume · $path"
        }
    }
}
