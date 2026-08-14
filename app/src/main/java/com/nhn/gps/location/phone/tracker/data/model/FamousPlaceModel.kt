package com.nhn.gps.location.phone.tracker.data.model

import com.google.android.libraries.places.api.model.PhotoMetadata

data class FamousPlaceModel(
    val id: String,
    val name: String,
    val location: String,
    val imageRes: Int,
    val rating: Float,
    val reviewCount: Int,
    val distanceKm: Double,
    val category: String,
    val isFavorite: Boolean = false,
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val phoneNumber: String? = null,
    val websiteUri: String? = null,
    val isOpen: Boolean? = null,
    val openingHours: List<String>? = null,
    val types: List<String>? = null,
    val address: String? = null,
    val photoMetadata: PhotoMetadata? = null,
    val idPlaceType: Int = 0,
    val descriptionResKey: String? = null,
    val previewPhotos: List<String> = emptyList()
)
