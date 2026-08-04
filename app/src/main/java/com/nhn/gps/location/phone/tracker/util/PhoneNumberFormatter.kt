package com.nhn.gps.location.phone.tracker.util

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PhoneNumberFormatter @Inject constructor() {

    /**
     * Chuẩn hóa số điện thoại về định dạng nội địa để tìm kiếm trong Database.
     * Ví dụ: 
     * - dialCode: "+84", input: "912345678" -> "0912345678"
     * - dialCode: "+84", input: "0912345678" -> "0912345678"
     * 
     * Thiết kế này dễ dàng mở rộng cho các quốc gia khác bằng cách thêm logic 
     * kiểm tra theo dialCode hoặc ISO code.
     */
    fun normalize(dialCode: String, phone: String): String {
        val cleanPhone = phone.trim().replace(" ", "").replace("-", "")
        
        // Hiện tại Database đang lưu theo định dạng số 0 ở đầu (Việt Nam)
        return when (dialCode) {
            "+84" -> {
                if (cleanPhone.startsWith("0")) {
                    cleanPhone
                } else if (cleanPhone.startsWith("+84")) {
                    "0" + cleanPhone.substring(3)
                } else if (cleanPhone.startsWith("84")) {
                    "0" + cleanPhone.substring(2)
                } else {
                    "0$cleanPhone"
                }
            }
            // Mở rộng thêm các quốc gia khác ở đây
            else -> {
                // Mặc định nếu không phải +84, có thể giữ nguyên hoặc xử lý theo quy tắc chung
                if (cleanPhone.startsWith("+")) cleanPhone else "$dialCode$cleanPhone"
            }
        }
    }
}
