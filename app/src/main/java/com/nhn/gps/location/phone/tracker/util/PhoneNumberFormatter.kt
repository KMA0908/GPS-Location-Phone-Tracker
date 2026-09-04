package com.nhn.gps.location.phone.tracker.util

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PhoneNumberFormatter @Inject constructor() {

    /** Stores and searches every phone number in one E.164-compatible format. */
    fun normalize(dialCode: String, phone: String): String {
        val raw = phone.trim()
        val normalizedDialCode = "+" + dialCode.filter(Char::isDigit).trimStart('0')
        val digits = raw.filter(Char::isDigit)
        val canonical = when {
            raw.startsWith("+") -> "+$digits"
            raw.startsWith("00") -> "+${digits.drop(2)}"
            else -> normalizedDialCode + digits.trimStart('0')
        }
        require(E164.matches(canonical)) { "Invalid phone number" }
        return canonical
    }

    fun isValid(dialCode: String, phone: String): Boolean =
        phone.isNotBlank() && runCatching { normalize(dialCode, phone) }.isSuccess

    fun toNationalNumber(dialCode: String, phone: String): String {
        val canonicalDialCode = "+" + dialCode.filter(Char::isDigit).trimStart('0')
        val canonicalPhone = phone.trim()
        return if (canonicalPhone.startsWith(canonicalDialCode)) {
            canonicalPhone.removePrefix(canonicalDialCode)
        } else {
            canonicalPhone
        }
    }

    private companion object {
        val E164 = Regex("^\\+[1-9]\\d{6,14}$")
    }
}
