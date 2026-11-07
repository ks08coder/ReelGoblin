package com.reelgoblin

import android.view.accessibility.AccessibilityNodeInfo

/**
 * Looks at the accessibility tree of Instagram / YouTube and answers two things:
 *  1. Are we inside the Reels / Shorts player right now?
 *  2. A "signature" of the reel on screen (author + caption text). A new signature = a new reel.
 *
 * Instagram and YouTube rename their view ids from time to time. If counting ever drifts,
 * edit ONLY the id lists below (use `adb logcat -s ReelGoblin` to see what the detector sees).
 */
object ReelDetector {
    class Info(val inReels: Boolean, val sig: String)

    const val IG = "com.instagram.android"
    const val YT = "com.google.android.youtube"

    // Parts of view ids that only exist while the vertical reel player is on screen.
    private val IG_MARK = listOf("clips_viewer", "clips_video_container", "clips_author", "clips_caption", "reel_viewer", "reels_viewer")
    private val YT_MARK = listOf("reel_recycler", "reel_player_page", "reel_watch", "shorts_player", "reel_time_bar", "reel_progress")
    // Parts of view ids whose text identifies WHICH reel is showing.
    private val IG_SIG = listOf("clips_author", "clips_caption", "clips_media")
    private val YT_SIG = listOf("reel_channel", "reel_player_page_container", "shorts_title", "reel_title")

    fun inspect(root: AccessibilityNodeInfo, pkg: String): Info {
        val marks = if (pkg == IG) IG_MARK else YT_MARK
        val sigIds = if (pkg == IG) IG_SIG else YT_SIG
        var inReels = false
        val sig = StringBuilder()
        val stack = ArrayDeque<AccessibilityNodeInfo>()
        stack.addLast(root)
        var visited = 0
        while (stack.isNotEmpty() && visited < 1200) {
            val n = stack.removeLast()
            visited++
            val id = n.viewIdResourceName
            if (id != null) {
                val short = id.substringAfter(":id/")
                val visible = n.isVisibleToUser
                if (visible && marks.any { short.contains(it) }) inReels = true
                // Instagram bottom bar: the Reels tab being selected also means "in reels".
                if (pkg == IG && short == "clips_tab" && n.isSelected) inReels = true
                if (visible && sigIds.any { short.contains(it) }) {
                    val t = (n.text ?: n.contentDescription)?.toString()
                    if (!t.isNullOrBlank() && sig.length < 240) sig.append(t.take(80)).append('|')
                }
            }
            for (i in 0 until n.childCount) n.getChild(i)?.let { stack.addLast(it) }
        }
        return Info(inReels, sig.toString())
    }
}
