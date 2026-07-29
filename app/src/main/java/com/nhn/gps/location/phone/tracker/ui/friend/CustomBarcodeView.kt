package com.nhn.gps.location.phone.tracker.ui.friend

import android.content.Context
import android.graphics.Rect
import android.util.AttributeSet
import com.journeyapps.barcodescanner.BarcodeView

/**
 * CustomBarcodeView cho phép thiết lập vùng quét (framingRect) thủ công
 * để khớp với UI tùy chỉnh mà không làm ảnh hưởng tới Preview toàn màn hình.
 */
class CustomBarcodeView(context: Context, attrs: AttributeSet?) : BarcodeView(context, attrs) {

    private var manualFramingRect: Rect? = null

    /**
     * Thiết lập vùng quét thủ công theo tọa độ trên View.
     */
    fun setManualFramingRect(rect: Rect) {
        manualFramingRect = rect
    }

    override fun calculateFramingRect(container: Rect?, surface: Rect?): Rect {
        // Nếu có vùng quét thủ công, ưu tiên sử dụng
        return manualFramingRect ?: super.calculateFramingRect(container, surface)
    }
}
