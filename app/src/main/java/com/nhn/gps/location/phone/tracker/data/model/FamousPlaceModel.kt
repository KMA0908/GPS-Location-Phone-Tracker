package com.nhn.gps.location.phone.tracker.data.model

data class FamousPlaceModel(
    val id: String,
    val name: String,
    val location: String,
    val imageRes: Int,
    val rating: Float,
    val reviewCount: Int,
    val distanceKm: Double,
    val category: String,
    val isFavorite: Boolean = false
)
