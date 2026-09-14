package eu.kanade.tachiyomi.ui.reels

import android.app.Activity
import android.app.PictureInPictureParams
import android.graphics.Rect

/**
 * Picture-in-picture entry point for the reels feed (B3.5, declutter redesign).
 *
 * The feed registers [handler] while it is the composed screen; [eu.kanade.tachiyomi.ui.main.MainActivity]
 * invokes it from `onUserLeaveHint` — the Home gesture during playback enters PiP (the
 * YouTube/PlayerActivity pattern), so no dedicated top-bar button is needed. The handler
 * itself applies the gates (feature toggle, real playback, not the offline playlist? — see
 * the registration site) and returns whether PiP was entered.
 */
object ReelsPip {

    @Volatile
    var handler: (() -> Boolean)? = null

    /** Full-screen hint: the reels surface is portrait, the system clamps to the max aspect. */
    fun buildParams(activity: Activity): PictureInPictureParams {
        val bounds = Rect(
            0,
            0,
            activity.resources.displayMetrics.widthPixels,
            activity.resources.displayMetrics.heightPixels,
        )
        return PictureInPictureParams.Builder()
            .setSourceRectHint(bounds)
            .build()
    }
}
