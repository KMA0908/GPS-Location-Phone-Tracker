package com.nhn.gps.location.phone.tracker.data.model

enum class ZoneType(val code: Int, val label: String) {
    HOME(0, "Home"),
    SCHOOL(1, "School"),
    WORK(2, "Work"),
    OTHER(3, "Other");

    companion object {
        fun fromCode(code: Int) = entries.firstOrNull { it.code == code } ?: OTHER
    }
}

enum class ZoneStatus(val code: Int, val label: String) {
    SAFE(0, "Safe"),
    DANGEROUS(1, "Dangerous");

    companion object {
        fun fromCode(code: Int) = entries.firstOrNull { it.code == code } ?: SAFE
    }
}

enum class ZoneAlertType {
    ENTER,
    LEAVE,
    NEAR_DANGEROUS,
    RETURNED_SAFE,
}

data class Zone(
    val id: Long,
    val name: String,
    val address: String,
    val latitude: Double,
    val longitude: Double,
    val type: ZoneType = ZoneType.OTHER,
    val radiusMeters: Int = 100,
    val onEnter: Boolean = true,
    val onLeave: Boolean = true,
    val status: ZoneStatus = ZoneStatus.SAFE,
    val createdAt: Long = System.currentTimeMillis(),
)

data class ZoneAlert(
    val id: Long,
    val zoneId: Long,
    val zoneName: String,
    val isEnter: Boolean,
    val status: ZoneStatus,
    val time: Long,
    val latitude: Double,
    val longitude: Double,
    val userName: String = "You",
    val type: ZoneAlertType = if (isEnter) ZoneAlertType.ENTER else ZoneAlertType.LEAVE,
    val userId: String = "",
)
