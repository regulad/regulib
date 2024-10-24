package xyz.regulad.regulib.compose

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.content.getSystemService

/**
 * A view for a [SensorEvent] that includes the sensor, accuracy, timestamp, and values.
 *
 * This is needed because the [SensorEvent] class is not a data class and does not provide a proper equals/hashCode implementation.
 */
data class SensorEventView(
    val sensor: Sensor,
    val accuracy: Int,
    val timestamp: Long,
    val values: FloatArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as SensorEventView

        if (sensor != other.sensor) return false
        if (accuracy != other.accuracy) return false
        if (timestamp != other.timestamp) return false
        if (!values.contentEquals(other.values)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = sensor.hashCode()
        result = 31 * result + accuracy
        result = 31 * result + timestamp.hashCode()
        result = 31 * result + values.contentHashCode()
        return result
    }
}

/**
 * Helper function to create sensor state for three-axis sensors.
 *
 * It's recommended to use this state with [derivedStateOf] to reduce the number of unnecessary recompositions.
 *
 * Example:
 *
 * ```
 * val sensorState by rememberSensorState(Sensor.TYPE_ACCELEROMETER)
 * val variableOfInterest by remember {
 *     derivedStateOf {
 *         sensorState?.values?.get(0) ?: 0f
 *     }
 * }
 * // or an effect
 * val shouldPerformAnAction by remember {
 *     derivedStateOf {
 *         variableOfInterest > 0
 *     }
 * }
 * ```
 *
 * @param sensorType The type of sensor to monitor
 * @return [State] containing [SensorEvent] (s) for the specified sensor
 */
@Composable
@Suppress("unused")
fun rememberSensorState(sensorType: Int): State<SensorEventView?> {
    val context = LocalContext.current

    val state = remember(sensorType) { mutableStateOf<SensorEventView?>(null) }

    val sensorManager = context.getSystemService<SensorManager>()
    val sensor = sensorManager?.getDefaultSensor(sensorType)

    DisposableEffect(sensor) {
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                state.value = SensorEventView(
                    sensor = event.sensor,
                    accuracy = event.accuracy,
                    timestamp = event.timestamp,
                    values = event.values
                )
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }

        sensorManager?.registerListener(
            listener,
            sensor,
            SensorManager.SENSOR_DELAY_UI
        )

        onDispose {
            sensorManager?.unregisterListener(listener)
        }
    }

    return state
}

@Preview
@Composable
fun SensorStatePreview() {
    Column {
        val accelerometerState by rememberSensorState(Sensor.TYPE_ACCELEROMETER)

        accelerometerState?.values?.forEachIndexed { index, value ->
            Text("Accel. $index: $value")
        }

        val gravityState by rememberSensorState(Sensor.TYPE_GRAVITY)

        gravityState?.values?.forEachIndexed { index, value ->
            Text("Gravity $index: $value")
        }

        val linearAccelerationState by rememberSensorState(Sensor.TYPE_LINEAR_ACCELERATION)

        linearAccelerationState?.values?.forEachIndexed { index, value ->
            Text("Linear Accel. $index: $value")
        }

        val gyroscopeState by rememberSensorState(Sensor.TYPE_GYROSCOPE)

        gyroscopeState?.values?.forEachIndexed { index, value ->
            Text("Gyro. $index: $value")
        }

        val magneticFieldState by rememberSensorState(Sensor.TYPE_MAGNETIC_FIELD)

        magneticFieldState?.values?.forEachIndexed { index, value ->
            Text("Mag. $index: $value")
        }
    }
}
