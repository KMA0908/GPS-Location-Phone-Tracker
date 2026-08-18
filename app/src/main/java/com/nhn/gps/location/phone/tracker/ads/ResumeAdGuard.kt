package com.nhn.gps.location.phone.tracker.ads

object ResumeAdGuard {
    private var systemDialogPending = false
    private var skipNextResumeAd = false

    @Synchronized
    fun onSystemDialogRequested() {
        systemDialogPending = true
        skipNextResumeAd = true
    }

    @Synchronized
    fun onSystemDialogFinished() {
        systemDialogPending = false
    }

    @Synchronized
    fun suppressNextResumeAd() {
        skipNextResumeAd = true
    }

    @Synchronized
    fun shouldSkipResumeAd(): Boolean {
        val skip = systemDialogPending || skipNextResumeAd
        skipNextResumeAd = false
        return skip
    }
}
