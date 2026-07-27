package com.nhn.gps.location.phone.tracker.ui.location

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * CompassManager chịu trách nhiệm quản lý cảm biến và tính toán góc phương vị (Bearing).
 * Sử dụng Accelerometer và Magnetic Field để lấy dữ liệu hướng thiết bị.
 */
@Singleton
class CompassManager @Inject constructor(
    @ApplicationContext context: Context
) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

    private val _bearing = MutableStateFlow(0f)

    /**
     * Bearing hiện tại của thiết bị (0..360 độ).
     */
    val bearing: StateFlow<Float> = _bearing.asStateFlow()

    private val lastAccelerometer = FloatArray(3)
    private val lastMagnetometer = FloatArray(3)
    private var lastAccelerometerSet = false
    private var lastMagnetometerSet = false

    /**
     * Bắt đầu theo dõi hướng thiết bị.
     */
    fun start() {
        if (accelerometer != null && magnetometer != null) {
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_UI)
            sensorManager.registerListener(this, magnetometer, SensorManager.SENSOR_DELAY_UI)
        }
    }

    /**
     * Dừng theo dõi hướng thiết bị.
     */
    fun stop() {
        sensorManager.unregisterListener(this)
        lastAccelerometerSet = false
        lastMagnetometerSet = false
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
            lowPass(event.values, lastAccelerometer)
            lastAccelerometerSet = true
        } else if (event.sensor.type == Sensor.TYPE_MAGNETIC_FIELD) {
            lowPass(event.values, lastMagnetometer)
            lastMagnetometerSet = true
        }

        if (lastAccelerometerSet && lastMagnetometerSet) {
            val r = FloatArray(9)
            val i = FloatArray(9)
            if (SensorManager.getRotationMatrix(r, i, lastAccelerometer, lastMagnetometer)) {
                val orientation = FloatArray(3)
                SensorManager.getOrientation(r, orientation)

                // Azimuth là góc xoay quanh trục Z (radian).
                val azimuthInRadians = orientation[0]
                // Chuyển đổi sang độ (degree) 0..360.
                var azimuthInDegrees = Math.toDegrees(azimuthInRadians.toDouble()).toFloat()
                if (azimuthInDegrees < 0) {
                    azimuthInDegrees += 360f
                }

                _bearing.value = azimuthInDegrees
            }
        }
    }

    /**
     * Thuật toán lọc thông thấp (Low-pass Filter) giúp giảm nhiễu và làm mượt chuyển động xoay.
     */
    private fun lowPass(input: FloatArray, output: FloatArray) {
        val alpha = 0.15f // Điều chỉnh từ 0..1 để cân bằng giữa độ nhạy và độ mượt.
        for (i in input.indices) {
            output[i] = output[i] + alpha * (input[i] - output[i])
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
