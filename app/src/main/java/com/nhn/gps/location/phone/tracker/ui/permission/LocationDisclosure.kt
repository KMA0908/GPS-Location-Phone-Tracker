package com.nhn.gps.location.phone.tracker.ui.permission

import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import com.nhn.gps.location.phone.tracker.R

fun Fragment.showLocationDisclosure(onContinue: () -> Unit) {
    if (!isAdded) return
    AlertDialog.Builder(requireContext())
        .setTitle(R.string.location_data_disclosure_title)
        .setMessage(R.string.location_data_disclosure_message)
        .setPositiveButton(R.string.continue_text) { _, _ -> onContinue() }
        .setNegativeButton(R.string.not_now, null)
        .show()
}
