package com.nhn.gps.location.phone.tracker.ui.zone

import androidx.annotation.DrawableRes
import com.nhn.gps.location.phone.tracker.R
import com.nhn.gps.location.phone.tracker.data.model.ZoneAlert
import com.nhn.gps.location.phone.tracker.data.model.ZoneStatus
import com.nhn.gps.location.phone.tracker.data.model.ZoneAlertType

@DrawableRes
fun ZoneAlert.iconRes(): Int = when (type) {
    ZoneAlertType.NEAR_DANGEROUS -> R.drawable.ic_near_dangerous_zone
    ZoneAlertType.RETURNED_SAFE -> R.drawable.ic_return_safe_zone
    ZoneAlertType.ENTER -> if (status == ZoneStatus.DANGEROUS) {
        R.drawable.ic_enter_dangerous_zone
    } else {
        R.drawable.ic_enter_zone
    }
    ZoneAlertType.LEAVE -> R.drawable.ic_left_zone
}
