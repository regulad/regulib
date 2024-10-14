package xyz.regulad.regulib.ble

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattServer
import android.os.Build
import androidx.annotation.RequiresPermission
import java.util.*

object BLEConst {
    // https://devzone.nordicsemi.com/f/nordic-q-a/561/what-does-cccd-mean
    val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
}

@RequiresPermission(android.Manifest.permission.BLUETOOTH_CONNECT)
fun BluetoothGattServer.versionAgnosticNotifyCharacteristicChanged(
    device: BluetoothDevice,
    characteristic: BluetoothGattCharacteristic,
    confirm: Boolean,
    value: ByteArray
) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        notifyCharacteristicChanged(device, characteristic, confirm, value)
    } else {
        @Suppress("DEPRECATION")
        characteristic.value = value
        @Suppress("DEPRECATION")
        notifyCharacteristicChanged(device, characteristic, confirm)
    }
}
