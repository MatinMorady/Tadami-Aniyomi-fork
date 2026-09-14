package eu.kanade.tachiyomi.ui.reels

import android.app.Activity
import android.content.pm.ActivityInfo

/**
 * Shared portrait lock for nested reels feeds (audit H1).
 *
 * Voyager's screen transition keeps BOTH screens composed: on feed→feed navigation the
 * incoming screen locks portrait first and the outgoing screen's onDispose runs last — a
 * naive "restore my previousOrientation" would unlock portrait (or, after a pop cascade,
 * restore a stale SENSOR_PORTRAIT captured from the previous holder). A holder count fixes
 * both: the ORIGINAL orientation is captured before the first acquire and restored only
 * after the last release. Main-thread only (composition/disposal order).
 */
object ReelsOrientationLock {

    private var holders = 0
    private var previousOrientation: Int = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED

    fun acquire(activity: Activity) {
        if (holders == 0) {
            previousOrientation = activity.requestedOrientation
        }
        holders++
        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
    }

    fun release(activity: Activity) {
        holders = (holders - 1).coerceAtLeast(0)
        if (holders == 0) {
            activity.requestedOrientation = previousOrientation
        }
    }
}
