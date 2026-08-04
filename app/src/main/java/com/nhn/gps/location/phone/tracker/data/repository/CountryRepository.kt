package com.nhn.gps.location.phone.tracker.data.repository

import android.content.Context
import android.util.Log
import com.hbb20.CCPCountry
import com.hbb20.CountryCodePicker
import com.nhn.gps.location.phone.tracker.data.model.Country
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

interface CountryRepository {
    fun getCountries(): List<Country>
}

@Singleton
class CountryRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : CountryRepository {

    private var cachedCountries: List<Country>? = null

    override fun getCountries(): List<Country> {
        cachedCountries?.let { return it }
        
        Log.d("CountryRepository", "Starting to load countries from CCP...")
        
        return try {
            // Thư viện CCP bản 2.5.1 yêu cầu truyền Language. 
            // Nếu truyền ENGLISH mà trả về rỗng, có thể do lỗi khởi tạo nội bộ của thư viện với Context.
            var ccpCountries = CCPCountry.getLibraryMasterCountryList(context, CountryCodePicker.Language.ENGLISH)
            
            Log.d("CountryRepository", "Initial load (ENGLISH) size: ${ccpCountries?.size ?: 0}")

            // Nếu vẫn rỗng, thử log chi tiết Context
            if (ccpCountries.isNullOrEmpty()) {
                Log.w("CountryRepository", "CCP returned empty list. Context package: ${context.packageName}")
            }

            val countries = ccpCountries?.map { ccpCountry ->
                val iso = ccpCountry.nameCode.uppercase()
                Country(
                    name = ccpCountry.name,
                    dialCode = "+${ccpCountry.phoneCode}",
                    iso = iso,
                    emoji = getEmojiFromIso(iso)
                )
            }?.sortedBy { it.name } ?: emptyList()
            
            cachedCountries = countries
            Log.d("CountryRepository", "Successfully mapped ${countries.size} countries.")
            countries
        } catch (e: Exception) {
            Log.e("CountryRepository", "Critical error loading countries: ${e.message}", e)
            emptyList()
        }
    }

    private fun getEmojiFromIso(iso: String): String {
        if (iso.length != 2) return ""
        return try {
            val firstLetter = Character.codePointAt(iso, 0) - 0x41 + 0x1F1E6
            val secondLetter = Character.codePointAt(iso, 1) - 0x41 + 0x1F1E6
            String(Character.toChars(firstLetter)) + String(Character.toChars(secondLetter))
        } catch (e: Exception) {
            ""
        }
    }
}
