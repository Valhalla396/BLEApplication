package com.example.bleapplication

import android.bluetooth.*
import android.content.Context
import android.util.Log

class BLEManager(private val context: Context) {

    private var bluetoothGatt: BluetoothGatt? = null
    private var bluetoothAdapter: BluetoothAdapter? = null

    private val serviceUUID = "12345678-1234-5678-1234-56789abcdef0"
    private val readCharacteristicUUID = "abcdef01-1234-5678-1234-56789abcdef0"
    private val writeCharacteristicUUID = "abcdef02-1234-5678-1234-56789abcdef0"

    // Initialisiere den BLE-Adapter
    fun initialize(): Boolean {
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter
        return bluetoothAdapter != null
    }

    // Verbindung zu einem BLE-Gerät herstellen
    fun connect(deviceAddress: String): Boolean {
        val device = bluetoothAdapter?.getRemoteDevice(deviceAddress) ?: return false
        bluetoothGatt = device.connectGatt(context, false, gattCallback)
        return true
    }

    // BLE-Gatt-Callback
    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.d("BLEManager", "Verbunden mit Gerät. Starte Dienstsuche...")
                gatt.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.d("BLEManager", "Verbindung getrennt.")
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d("BLEManager", "Dienste gefunden.")
                // Prüfe, ob die gewünschten Dienste und Charakteristiken vorhanden sind
                val service = gatt.getService(java.util.UUID.fromString(serviceUUID))
                if (service != null) {
                    Log.d("BLEManager", "Dienst gefunden: $serviceUUID")
                } else {
                    Log.d("BLEManager", "Dienst nicht gefunden.")
                }
            }
        }

        override fun onCharacteristicRead(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                if (characteristic.uuid.toString() == readCharacteristicUUID) {
                    val value = characteristic.value
                    Log.d("BLEManager", "Gelesene Daten: ${value.decodeToString()}")
                }
            }
        }

        override fun onCharacteristicWrite(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            if (characteristic.uuid.toString() == writeCharacteristicUUID) {
                Log.d("BLEManager", "Eigenschaft geschrieben: ${characteristic.uuid}")
            }
        }
    }

    // Daten schreiben
    fun writeCharacteristic(data: ByteArray) {
        val service = bluetoothGatt?.getService(java.util.UUID.fromString(serviceUUID))
        val characteristic = service?.getCharacteristic(java.util.UUID.fromString(writeCharacteristicUUID))
        characteristic?.value = data
        bluetoothGatt?.writeCharacteristic(characteristic)
    }

    // Daten lesen
    fun readCharacteristic() {
        val service = bluetoothGatt?.getService(java.util.UUID.fromString(serviceUUID))
        val characteristic = service?.getCharacteristic(java.util.UUID.fromString(readCharacteristicUUID))
        bluetoothGatt?.readCharacteristic(characteristic)
    }

    // Verbindung trennen
    fun disconnect() {
        bluetoothGatt?.disconnect()
        bluetoothGatt = null
    }
}
