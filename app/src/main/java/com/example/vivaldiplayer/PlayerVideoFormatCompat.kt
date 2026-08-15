package com.example.vivaldiplayer

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi

/**
 * Compatibility view of the currently selected video format for the Media3
 * version pinned by this project.
 *
 * Newer Media3 revisions expose a direct Player video-format accessor, but the
 * pinned 1.10.1 Player interface does not. The player still exposes currentTracks,
 * including whether each track is selected, and Player.Listener provides
 * onVideoSizeChanged for the actual rendered dimensions.
 *
 * PlayerChromeProvider uses this helper only as a conservative fallback:
 * - if Media3 selection identifies exactly one video height, return a Format for
 *   that height;
 * - if an adaptive group has several selected heights, return null rather than
 *   pretending the highest available rendition is the one currently rendered;
 * - the onVideoSizeChanged callback remains the stronger runtime evidence in that
 *   adaptive case.
 *
 * This helper never changes track selection and never creates another player.
 */
@OptIn(UnstableApi::class)
internal val Player.videoFormat: Format?
    get() {
        val selectedFormats = currentTracks.groups
            .filter { group -> group.type == C.TRACK_TYPE_VIDEO }
            .flatMap { group ->
                (0 until group.length).mapNotNull { index ->
                    if (!group.isTrackSupported(index) || !group.isTrackSelected(index)) {
                        null
                    } else {
                        group.getTrackFormat(index).takeIf { format -> format.height > 0 }
                    }
                }
            }

        val selectedHeights = selectedFormats
            .map { format -> format.height }
            .distinct()

        if (selectedHeights.size != 1) return null

        /*
         * Several codec/bitrate variants can share one height. Any of them proves
         * the selected height; returning the highest-bitrate entry keeps the
         * representative Format deterministic without claiming a different size.
         */
        return selectedFormats.maxByOrNull { format ->
            maxOf(format.averageBitrate, format.peakBitrate)
        }
    }
