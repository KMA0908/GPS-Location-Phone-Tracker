package com.nhn.gps.location.phone.tracker.data.repository

/** Firebase Realtime Database child keys cannot contain separators or controls. */
internal object FirebasePathKey {
    private val forbiddenCharacters = Regex("[.#$\\[\\]/\\u0000-\\u001F\\u007F]")

    fun isValid(value: String, maxLength: Int = DEFAULT_MAX_LENGTH): Boolean =
        value.isNotBlank() && value.length <= maxLength && !forbiddenCharacters.containsMatchIn(value)

    fun requireValid(
        value: String,
        label: String,
        maxLength: Int = DEFAULT_MAX_LENGTH,
    ) {
        require(isValid(value, maxLength)) { "$label is invalid" }
    }

    private const val DEFAULT_MAX_LENGTH = 128
}
