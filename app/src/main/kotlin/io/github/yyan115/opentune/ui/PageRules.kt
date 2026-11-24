package io.github.yyan115.opentune.ui

import io.github.yyan115.opentune.core.MixHeader
import io.github.yyan115.opentune.core.PageData
import io.github.yyan115.opentune.core.TrackListModule

/**
 * Whether the lists of songs of a page show every song and not a preview of five. A page of a mix is its list of songs below a header, and the list
 * has no title, so no row exists on which "View all" could sit. The preview belongs to a list inside a page of many sections, such as the top songs
 * of an artist, which has a title row with "View all".
 */
fun PageData.showsEverySong(): Boolean = modules.any { it is MixHeader }

/** Whether TIDAL has more songs for this list than the [shown] ones, and says where to ask for them. */
fun TrackListModule.hasMore(shown: Int): Boolean = dataPath != null && shown < total
