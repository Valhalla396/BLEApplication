package com.example.bleapplication

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.pm.PackageManager
import android.os.*
import android.util.Log
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.util.*

class MainActivity : AppCompatActivity() {

    private val REQUEST_PERMISSION_LOCATION = 1

    private lateinit var bluetoothAdapter: BluetoothAdapter
    private var bluetoothGatt: BluetoothGatt? = null

    private lateinit var seekBar: SeekBar
    private lateinit var percentageTextView: TextView
    private lateinit var switchView: Switch
    private lateinit var applyButton: Button
    private lateinit var connectButton: Button
    private lateinit var characteristicValueTextView: TextView
    private lateinit var progressBar: ProgressBar


    private val handler = Handler(Looper.getMainLooper())

    // Advertising UUID (zur Geräteidentifikation)
    private val ADVERTISING_UUID = UUID.fromString("c8bac71f-579e-4d69-b18e-83639e15e705")

    // Service- und Charakteristik-UUIDs
    private val SERVICE_UUID = UUID.fromString("12345678-1234-5678-1234-56789abcdef0")
    private val READ_CHARACTERISTIC_UUID = UUID.fromString("abcdef01-1234-5678-1234-56789abcdef0")
    private val PERCENTAGE_CHARACTERISTIC_UUID = UUID.fromString("abcdef02-1234-5678-1234-56789abcdef0")
    private val SWITCH_CHARACTERISTIC_UUID = UUID.fromString("abcdef03-1234-5678-1234-56789abcdef0")

    private lateinit var bluetoothLeScanner: BluetoothLeScanner
    private var scanning = false
    private val SCAN_PERIOD: Long = 10000 // 10 Sekunden Scanzeit

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialisierung der UI-Elemente
        seekBar = findViewById(R.id.seekBar)
        percentageTextView = findViewById(R.id.percentageTextView)
        switchView = findViewById(R.id.switchView)
        applyButton = findViewById(R.id.applyButton)
        connectButton = findViewById(R.id.connectButton)
        characteristicValueTextView = findViewById(R.id.characteristicValueTextView)
        progressBar = findViewById(R.id.progressBar)


        // Hinzufügen des Switch-Listeners
        switchView.setOnCheckedChangeListener { _, isChecked ->
            sendSwitchValue(isChecked) // Übergibt den Boolean-Wert korrekt
        }

        // Weitere UI-Listener und Bluetooth-Initialisierung
        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                percentageTextView.text = "$progress%"
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        applyButton.setOnClickListener {
            val percentage = seekBar.progress
            sendPercentageValue(percentage) // Sendet den Prozentwert
        }

        connectButton.setOnClickListener {
            checkPermissionsAndScan() // Startet den Scan
        }

        initBluetooth() // Bluetooth initialisieren
    }


    private fun initBluetooth() {
        val bluetoothManager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter
        bluetoothLeScanner = bluetoothAdapter.bluetoothLeScanner
    }

    private fun checkPermissionsAndScan() {
        val permissions = mutableListOf<String>()

        if (!hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)) {
            permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!hasPermission(Manifest.permission.BLUETOOTH_SCAN)) {
                permissions.add(Manifest.permission.BLUETOOTH_SCAN)
            }
            if (!hasPermission(Manifest.permission.BLUETOOTH_CONNECT)) {
                permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
            }
        }

        if (permissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, permissions.toTypedArray(), REQUEST_PERMISSION_LOCATION)
        } else {
            startScan()
        }
    }

    private fun hasPermission(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == REQUEST_PERMISSION_LOCATION) {
            if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                startScan()
            } else {
                Toast.makeText(this, "Berechtigungen abgelehnt. Bluetooth-Funktionen sind eingeschränkt.", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun startScan() {
        if (!hasPermission(Manifest.permission.BLUETOOTH_SCAN)) {
            Toast.makeText(this, "Berechtigung BLUETOOTH_SCAN benötigt", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            if (!bluetoothAdapter.isEnabled) {
                Toast.makeText(this, "Bluetooth ist deaktiviert. Bitte aktivieren Sie Bluetooth.", Toast.LENGTH_SHORT).show()
                return
            }

            if (scanning) {
                Toast.makeText(this, "Scan läuft bereits...", Toast.LENGTH_SHORT).show()
                return
            }

            val filters: MutableList<ScanFilter> = ArrayList()
            val scanFilter = ScanFilter.Builder()
                .setServiceUuid(ParcelUuid(ADVERTISING_UUID)) // Filter auf Ihre UUID
                .build()
            filters.add(scanFilter)

            val settings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build()

            handler.postDelayed({
                if (scanning) {
                    bluetoothLeScanner.stopScan(scanCallback)
                    scanning = false
                    Toast.makeText(this, "Scan beendet. Kein Gerät gefunden.", Toast.LENGTH_SHORT).show()
                }
            }, SCAN_PERIOD)

            bluetoothLeScanner.startScan(filters, settings, scanCallback)
            scanning = true
            Toast.makeText(this, "Suche nach Geräten...", Toast.LENGTH_SHORT).show()
        } catch (e: SecurityException) {
            Toast.makeText(this, "Scan fehlgeschlagen: Keine Berechtigung", Toast.LENGTH_SHORT).show()
            Log.e("BLE", "SecurityException beim Starten des Scans", e)
        }
    }

    private val scanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            super.onScanResult(callbackType, result)
            result?.device?.let { device ->
                if (scanning) {
                    bluetoothLeScanner.stopScan(this)
                    scanning = false
                }
                connectToDevice(device)
            }
        }

        override fun onScanFailed(errorCode: Int) {
            super.onScanFailed(errorCode)
            Toast.makeText(this@MainActivity, "Scan fehlgeschlagen: $errorCode", Toast.LENGTH_SHORT).show()
        }
    }

    @SuppressLint("MissingPermission")
    private fun connectToDevice(device: BluetoothDevice) {
        if (!hasPermission(Manifest.permission.BLUETOOTH_CONNECT)) {
            Toast.makeText(this, "Berechtigung BLUETOOTH_CONNECT benötigt", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            bluetoothGatt = device.connectGatt(this, false, gattCallback)
            Toast.makeText(this, "Verbinde mit ${device.name ?: "Gerät"}", Toast.LENGTH_SHORT).show()
        } catch (e: SecurityException) {
            Toast.makeText(this, "Verbindung fehlgeschlagen: Keine Berechtigung", Toast.LENGTH_SHORT).show()
            Log.e("BLE", "SecurityException beim Verbinden mit dem Gerät", e)
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {

        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            super.onConnectionStateChange(gatt, status, newState)
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    Log.d("BluetoothGatt", "Verbunden mit GATT-Server")
                    runOnUiThread {
                        Toast.makeText(this@MainActivity, "Verbunden", Toast.LENGTH_SHORT).show()
                    }
                    try {
                        if (hasPermission(Manifest.permission.BLUETOOTH_CONNECT)) {
                            gatt?.discoverServices()
                        }
                    } catch (e: SecurityException) {
                        Toast.makeText(this@MainActivity, "Fehler: Keine Berechtigung für Discover Services", Toast.LENGTH_SHORT).show()
                        Log.e("BLE", "SecurityException beim Entdecken der Services", e)
                    }
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    Log.d("BluetoothGatt", "Verbindung zum GATT-Server verloren")
                    runOnUiThread {
                        Toast.makeText(this@MainActivity, "Verbindung verloren", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            super.onServicesDiscovered(gatt, status)
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d("BLE", "Services erfolgreich entdeckt")
                handler.post(readRunnable)
            } else {
                Log.e("BLE", "Service-Erkennung fehlgeschlagen mit Status $status")
            }
        }

        override fun onCharacteristicRead(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic?,
            status: Int
        ) {
            super.onCharacteristicRead(gatt, characteristic, status)
            if (status == BluetoothGatt.GATT_SUCCESS) {
                val value = characteristic?.value
                if (value != null && value.isNotEmpty()) {
                    val intValue = value[0].toInt() // Ersten Bytewert verwenden

                    Log.d("BLE", "Charakteristik gelesen: UUID=${characteristic.uuid}, Wert=$intValue")
                    runOnUiThread {
                        // ProgressBar aktualisieren
                        progressBar.progress = intValue
                        characteristicValueTextView.text = "$intValue%"
                    }
                } else {
                    Log.e("BLE", "Keine Daten empfangen.")
                }
            } else {
                Log.e("BLE", "Fehler beim Lesen der Charakteristik: Status $status")
            }
        }



        override fun onCharacteristicWrite(gatt: BluetoothGatt?, characteristic: BluetoothGattCharacteristic?, status: Int) {
            super.onCharacteristicWrite(gatt, characteristic, status)
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d("BLE", "Charakteristik geschrieben: UUID=${characteristic?.uuid}")
            } else {
                Log.e("BLE", "Fehler beim Schreiben der Charakteristik: Status $status")
            }
        }
    }

    private val readRunnable = object : Runnable {
        override fun run() {
            readSpecificCharacteristic()
            handler.postDelayed(this, 3000) // Alle 3 Sekunden aktualisieren
        }
    }

    @SuppressLint("MissingPermission")
    private fun readSpecificCharacteristic() {
        if (!hasPermission(Manifest.permission.BLUETOOTH_CONNECT)) {
            Toast.makeText(this, "Berechtigung BLUETOOTH_CONNECT benötigt", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val service = bluetoothGatt?.getService(SERVICE_UUID)
            if (service == null) {
                Log.e("BLE", "Service nicht gefunden: $SERVICE_UUID")
                return
            }

            val characteristic = service.getCharacteristic(READ_CHARACTERISTIC_UUID)
            if (characteristic == null) {
                Log.e("BLE", "Charakteristik nicht gefunden: $READ_CHARACTERISTIC_UUID")
                return
            }

            val result = bluetoothGatt?.readCharacteristic(characteristic)
            Log.d("BLE", "Lesevorgang gestartet: $result")
        } catch (e: SecurityException) {
            Log.e("BLE", "Fehler beim Lesen der Charakteristik: Keine Berechtigung", e)
        }
    }

    @SuppressLint("MissingPermission")
    private fun sendPercentageValue(percentage: Int) {
        if (!hasPermission(Manifest.permission.BLUETOOTH_CONNECT)) {
            Toast.makeText(this, "Berechtigung BLUETOOTH_CONNECT benötigt", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val service = bluetoothGatt?.getService(SERVICE_UUID)
            if (service == null) {
                Log.e("BLE", "Service nicht gefunden: $SERVICE_UUID")
                return
            }

            val characteristic = service.getCharacteristic(PERCENTAGE_CHARACTERISTIC_UUID)
            if (characteristic == null) {
                Log.e("BLE", "Charakteristik nicht gefunden: $PERCENTAGE_CHARACTERISTIC_UUID")
                return
            }

            // Konvertiere den Wert in ein Byte und logge den genauen Wert
            val byteValue = percentage.toByte()
            Log.d("BLE", "Sende Prozentwert: $percentage als Byte: ${byteValue.toInt()}")

            // Setze den Byte-Wert in die Charakteristik
            characteristic.value = byteArrayOf(byteValue)

            // Sende die Charakteristik
            val result = bluetoothGatt?.writeCharacteristic(characteristic)
            Log.d("BLE", "Prozentwert gesendet: $percentage, Ergebnis: $result")
        } catch (e: SecurityException) {
            Log.e("BLE", "Fehler beim Schreiben der Charakteristik: Keine Berechtigung", e)
        }
    }






    @SuppressLint("MissingPermission")
    private fun sendSwitchValue(isOn: Boolean) {
        if (!hasPermission(Manifest.permission.BLUETOOTH_CONNECT)) {
            Toast.makeText(this, "Berechtigung BLUETOOTH_CONNECT benötigt", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val service = bluetoothGatt?.getService(SERVICE_UUID)
            val characteristic = service?.getCharacteristic(SWITCH_CHARACTERISTIC_UUID)
            if (characteristic == null) {
                Log.e("BLE", "Charakteristik nicht gefunden: $SWITCH_CHARACTERISTIC_UUID")
                return
            }

            // Zustand in Byte (1 für "An", 0 für "Aus") konvertieren
            val stateByte = if (isOn) 1 else 0
            characteristic.value = byteArrayOf(stateByte.toByte())
            val result = bluetoothGatt?.writeCharacteristic(characteristic)
            Log.d("BLE", "Schalterzustand gesendet: ${if (isOn) "An" else "Aus"}, Ergebnis: $result")
        } catch (e: SecurityException) {
            Log.e("BLE", "Fehler beim Senden des Schalterzustands", e)
        }
    }





    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(readRunnable)

        try {
            if (hasPermission(Manifest.permission.BLUETOOTH_CONNECT)) {
                bluetoothGatt?.close()
            }
            bluetoothGatt = null
        } catch (e: SecurityException) {
            Log.e("BLE", "SecurityException beim Schließen der GATT-Verbindung", e)
        }
    }
}
