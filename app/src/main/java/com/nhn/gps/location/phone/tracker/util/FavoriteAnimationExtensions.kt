package com.nhn.gps.location.phone.tracker.util

import android.widget.ImageView
import androidx.annotation.DrawableRes

/**
 * Common favorite animation for the app.
 * Scale down -> change drawable -> scale up -> reset.
 */
fun ImageView.animateFavoriteChange(@DrawableRes targetIconRes: Int, animate: Boolean) {
    if (!animate) {
        animate().cancel()
        setImageResource(targetIconRes)
        scaleX = 1f
        scaleY = 1f
        alpha = 1f
        return
    }

    animate().cancel()
    scaleX = 1f
    scaleY = 1f
    animate()
        .scaleX(0.8f)
        .scaleY(0.8f)
        .setDuration(100)
        .withEndAction {
            setImageResource(targetIconRes)
            animate()
                .scaleX(1.2f)
                .scaleY(1.2f)
                .setDuration(100)
                .withEndAction {
                    animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(100)
                        .start()
                }
                .start()
        }
        .start()
}
